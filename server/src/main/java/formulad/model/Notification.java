package formulad.model;

import formulad.Client;

import java.io.Serializable;

public abstract class Notification implements Serializable {
    private String playerId;

    Notification(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public abstract void notify(Client client);
}
