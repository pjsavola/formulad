package gp;

import gp.ai.TrackData;
import gp.model.Kick;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.Level;

public class Lobby extends Thread {

    private final ServerSocket serverSocket;
    private PlayerSlot[] slots;
    private TrackData data;
    volatile boolean done;
    private final List<RemoteAI> clients = new ArrayList<>();
    private final Map<UUID, RemoteAI> clientMap = new HashMap<>();
    Lobby(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    void setSlots(PlayerSlot[] slots) {
        this.slots = slots;
    }

    void setTrack(TrackData data) {
        if (!data.equals(this.data)) {
            this.data = data;
            synchronized (clientMap) {
                final Set<UUID> idsToKick = new HashSet<>();
                for (RemoteAI client : clients) {
                    final ProfileMessage reply = client.getProfile(data);
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
                        final ProfileMessage message = client.getProfile(data);
                        if (message != null) {
                            synchronized (clientMap) {
                                final RemoteAI old = clientMap.put(message.getId(), client);
                                if (old != null) {
                                    for (PlayerSlot usedSlot : slots) {
                                        if (usedSlot.getProfile().getId().equals(message.getId()))
                                        {
                                            usedSlot.setProfile(message);
                                            System.out.println("Client reconnected: " + message.getName());
                                            usedSlot.repaint();
                                            slot.setProfile((ProfileMessage) null);
                                            slot.setEnabled(true);
                                            slot.repaint();
                                            clients.remove(old);
                                            clients.add(client);
                                            continue waiting;
                                        }
                                    }
                                }
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
                Main.log.log(Level.WARNING, "Server IOException", e);
            }
        }
    }

    RemoteAI getClient(UUID id) {
        // Does not need to be synchronized. Map is not mutated anymore.
        return clientMap.get(id);
    }

    void dropClient(UUID id) {
        synchronized (clientMap) {
            final RemoteAI client = clientMap.remove(id);
            if (client != null) {
                client.notify(new Kick("Sorry"));
                clients.remove(client);
                client.close();
            }
        }
    }

    void close() {
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
