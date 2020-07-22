package gp;

import gp.model.Kick;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.Level;

public class Lobby extends Thread {

    private final ServerSocket serverSocket;
    private PlayerSlot[] slots;
    private String trackId;
    private boolean external;
    volatile boolean done;
    final List<RemoteAI> clients = new ArrayList<>();
    final Map<UUID, RemoteAI> clientMap = new HashMap<>();
    public Lobby(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public void setSlots(PlayerSlot[] slots) {
        this.slots = slots;
    }

    public void setTrack(String trackId, boolean external) {
        if (!trackId.equals(this.trackId)) {
            this.trackId = trackId;
            this.external = external;
            synchronized (clientMap) {
                final Set<UUID> idsToKick = new HashSet<>();
                for (RemoteAI client : clients) {
                    final ProfileMessage reply = client.getProfile(new ProfileRequest(trackId, external));
                    if (reply == null) {
                        clientMap.entrySet().stream().filter(e -> e.getValue() == client).map(Map.Entry::getKey).findFirst().ifPresent(idsToKick::add);
                    }
                }
                if (!idsToKick.isEmpty()) {
                    for (PlayerSlot slot : slots) {
                        if (idsToKick.contains(slot.getProfile().getId())) {
                            dropClient(slot.getProfile().getId());
                            slot.setProfile((ProfileMessage) null);
                            slot.setEnabled(true);
                        }
                    }
                }
            }
        }
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
                        final ProfileMessage message = client.getProfile(new ProfileRequest(trackId, external));
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
                Main.log.log(Level.SEVERE, "Server IOException", e);
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
            client.notify(new Kick("Server closed"));
            client.close();
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            Main.log.log(Level.SEVERE, "Lobby IOException", e);
        }
        done = true;
    }
}
