package gp;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Level;

import gp.ai.AI;

import gp.ai.GreatAI;
import gp.ai.TrackData;
import gp.model.*;

public class RemoteAI implements AI {

    private static final int heartBeatMs = 200;
    private final Socket socket;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private GreatAI fallback;
    private GameState gameState;
    private int gear;

    public RemoteAI(Socket clientSocket) {
        this.socket = clientSocket;
        try {
            ois = new ObjectInputStream(socket.getInputStream());
            oos = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            Main.log.log(Level.SEVERE, "Error when initializing Client " + socket.getInetAddress().toString(), e);
            close();
        }
    }

    boolean isConnected() {
        return ois != null && oos != null;
    }

    private Object getResponse() throws IOException, ClassNotFoundException {
        try {
            return ois.readObject();
        } catch (EOFException e) {
            // this might be ok if there's no response yet
            // test whether connection still exists by writing empty notification
            try {
                Thread.sleep(heartBeatMs);
            } catch (InterruptedException exception) {
                e.printStackTrace();
            }
            oos.writeObject(new Notification(""));
        }
        return null;
    }

    public void close() {
        try {
            if (ois != null) {
                ois.close();
            }
            if (oos != null) {
                oos.close();
            }
            socket.close();
        } catch (IOException e) {
            Main.log.log(Level.WARNING, "Error when terminating Client " + socket.getInetAddress().toString(), e);
        }
        ois = null;
        oos = null;
    }

    public ProfileMessage getProfile(TrackData data) {
        if (oos != null && ois != null) {
            fallback = new GreatAI(data);
            try {
                oos.writeObject(data);
                Object response;
                do {
                    response = getResponse();
                } while (!(response instanceof ProfileMessage));
                return (ProfileMessage) response;
            } catch (IOException | ClassNotFoundException e) {
                close();
            }
        }
        return null;
    }

    @Override
    public Gear selectGear(GameState gameState) {
        this.gameState = gameState;
        if (oos != null && ois != null) {
            try {
                oos.writeObject(gameState);
                Object response;
                do {
                    response = getResponse();
                } while (!(response instanceof Gear));
                gear = ((Gear) response).getGear();
                return (Gear) response;
            } catch (IOException | ClassNotFoundException e) {
                close();
                Main.log.log(Level.WARNING, "Lost connection to client, using fallback AI instead", e);
            }
        }
        if (fallback != null) {
            return fallback.selectGear(gameState);
        }
        return null;
    }

    @Override
    public SelectedIndex selectMove(Moves allMoves) {
        if (oos != null && ois != null) {
            try {
                oos.writeObject(allMoves);
                Object response;
                do {
                    response = getResponse();
                } while (!(response instanceof SelectedIndex));
                return (SelectedIndex) response;
            } catch (IOException | ClassNotFoundException e) {
                close();
                fallback.init(gameState, gear);
                Main.log.log(Level.WARNING, "Lost connection to client, using fallback AI instead", e);
            }
        }
        if (fallback != null) {
            return fallback.selectMove(allMoves);
        }
        return null;
    }

    @Override
    public void notify(Object notification) {
        if (oos != null) {
            try {
                oos.writeObject(notification);
            } catch (IOException e) {
                Main.log.log(Level.WARNING, "Error when notifying client", e);
            }
        }
    }
}
