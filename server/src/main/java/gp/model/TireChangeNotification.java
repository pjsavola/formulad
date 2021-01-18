package gp.model;

import gp.Client;

import java.io.Serializable;

public class TireChangeNotification extends Notification implements Serializable {
    private final Tires tires;

    public TireChangeNotification(String playerId, Tires tires) {
        super(playerId);
        this.tires = tires;
    }

    public Tires getTires() {
        return tires;
    }

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
