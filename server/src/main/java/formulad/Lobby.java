package formulad;

import javax.swing.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

public class Lobby extends Thread {

    private final ServerSocket serverSocket;
    private final int playerCount;
    private final JLabel label;
    final List<RemoteAI> clients = new ArrayList<>();
    public Lobby(int port, int playerCount, JLabel label) throws IOException {
        serverSocket = new ServerSocket(port);
        this.playerCount = playerCount;
        this.label = label;
    }

    @Override
    public void run() {
        while (playerCount + clients.size() < 10) {
            System.out.println("Waiting for clients");
            try {
                final Socket socket = serverSocket.accept();
                System.out.println("Client connected: " + socket.getInetAddress().toString());
                clients.add(new RemoteAI(socket));
                label.setText("Connected clients: " + clients.size());
                label.repaint();
            } catch (IOException e) {
                FormulaD.log.log(Level.SEVERE, "Server IOException", e);
            }
        }
    }

    public void notifyClients(Object notification) {
        for (RemoteAI client : clients) {
            client.notify(notification);
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
    }
}
