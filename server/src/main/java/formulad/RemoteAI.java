package formulad;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Level;

import formulad.ai.AI;

import formulad.model.GameState;
import formulad.model.Gear;
import formulad.model.Moves;
import formulad.model.NameAtStart;
import formulad.model.SelectedIndex;
import formulad.model.Track;

public class RemoteAI implements AI {

    private final Socket socket;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;

    public RemoteAI(Socket clientSocket) {
        this.socket = clientSocket;
        try {
            ois = new ObjectInputStream(socket.getInputStream());
            oos = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            FormulaD.log.log(Level.SEVERE, "Error when initializing Client " + socket.getInetAddress().toString(), e);
        }
    }

    private Object getResponse() {
        try {
            return ois.readObject();
        } catch (EOFException e) {
            // this is ok, no response yet
        } catch (IOException e) {
            FormulaD.log.log(Level.SEVERE, "IOException from Client " + socket.getInetAddress().toString(), e);
        } catch (ClassNotFoundException e) {
            FormulaD.log.log(Level.SEVERE, "ClassNotFoundException from Client " + socket.getInetAddress().toString(), e);
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
            FormulaD.log.log(Level.SEVERE, "Error when terminating Client " + socket.getInetAddress().toString(), e);
        }
    }

    @Override
    public NameAtStart startGame(Track track) {
        if (oos != null && ois != null) {
            try {
                oos.writeObject(track);
                Object response;
                do {
                    response = getResponse();
                } while (!(response instanceof NameAtStart));
                return (NameAtStart) response;
            } catch (IOException e) {
                FormulaD.log.log(Level.SEVERE, "Error in remote AI", e);
            }
        }
        return null;
    }

    @Override
    public Gear selectGear(GameState gameState) {
        if (oos != null && ois != null) {
            try {
                oos.writeObject(gameState);
                Object response;
                do {
                    response = getResponse();
                } while (!(response instanceof Gear));
                return (Gear) response;
            } catch (IOException e) {
                FormulaD.log.log(Level.SEVERE, "Error in remote AI", e);
            }
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
            } catch (IOException e) {
                FormulaD.log.log(Level.SEVERE, "Error in remote AI", e);
            }
        }
        return null;
    }
}
