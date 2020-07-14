package formulad.model;

import formulad.Client;

import java.io.Serializable;

public class Notification implements Serializable {
    private String playerId;

    public Notification(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void notify(Client client) {
    }
}
