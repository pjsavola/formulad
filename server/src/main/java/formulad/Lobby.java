package formulad;

import formulad.model.Kick;

import javax.swing.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.Level;

public class Lobby extends Thread {

    private final ServerSocket serverSocket;
    private PlayerSlot[] slots;
    volatile boolean done;
    final List<RemoteAI> clients = new ArrayList<>();
    final Map<UUID, RemoteAI> clientMap = new HashMap<>();
    public Lobby(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public void setSlots(PlayerSlot[] slots) {
        this.slots = slots;
    }

    @Override
    public void run() {
        waiting:
        while (!done) {
            System.out.println("Waiting for clients");
            try {
                final Socket socket = serverSocket.accept();
                for (PlayerSlot slot : slots) {
                    if (slot.isFree()) {
                        slot.setProfile(ProfileMessage.pending);
                        final RemoteAI client = new RemoteAI(socket);
                        final ProfileMessage message = client.getProfile(new ProfileRequest("sebring"));
                        if (message != null) {
                            synchronized (clientMap) {
                                clientMap.put(message.getId(), client);
                                clients.add(client);
                            }
                            slot.setProfile(message);
                            System.out.println("Client connected: " + message.getName());
                            slot.setEnabled(true);
                            slot.repaint();
                        }
                        continue waiting;
                    }
                }
                // The game is full :(
                socket.close();
            } catch (IOException e) {
                FormulaD.log.log(Level.SEVERE, "Server IOException", e);
            }
        }
    }

    public RemoteAI getClient(UUID id) {
        // Does not need to be synchronized. Map is not mutated anymore.
        return clientMap.get(id);
    }

    public void dropClient(UUID id) {
        synchronized (clientMap) {
            final RemoteAI client = clientMap.remove(id);
            if (client != null) {
                client.notify(new Kick("Sorry"));
                clients.remove(client);
                client.close();
            }
        }
    }

    public void close() {
        for (RemoteAI client : clients) {
            client.close();
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            FormulaD.log.log(Level.SEVERE, "Lobby IOException", e);
        }
        done = true;
    }
}
