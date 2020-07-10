package formulad;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class Server extends Thread {

    private final ServerSocket serverSocket;
    private final JLabel label;
    private boolean ready;
    final List<RemoteAI> clients = new ArrayList<>();
    public Server(int port, JLabel label) throws IOException {
        serverSocket = new ServerSocket(port);
        this.label = label;
    }

    @Override
    public void run() {
        try {
            while (!ready) {
                System.out.println("Waiting for clients");
                final Socket socket = serverSocket.accept();
                System.out.println("Client connected: " + socket.getInetAddress().toString());
                clients.add(new RemoteAI(socket));
                label.setText("Connected clients: " + clients.size());
                label.repaint();
            }
        } catch (IOException e) {
            FormulaD.log.log(Level.SEVERE, "Server IOException", e);
        }
    }

    public void ready() {
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }

    public void close() {
        for (RemoteAI client : clients) {
            client.close();
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            FormulaD.log.log(Level.SEVERE, "Server IOException", e);
        }
    }
}
