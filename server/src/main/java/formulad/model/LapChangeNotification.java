package formulad.model;

import formulad.Client;

import java.io.Serializable;

public class LapChangeNotification extends Notification implements Serializable {
    private final int lapsRemaining;

    public LapChangeNotification(String playerId, int lapsRemaining) {
        super(playerId);
        this.lapsRemaining = lapsRemaining;
    }

    public int getLapsRemaining() {
        return lapsRemaining;
    }

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
