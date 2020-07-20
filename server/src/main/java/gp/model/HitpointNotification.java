package gp.model;

import gp.Client;

import java.io.Serializable;

public class HitpointNotification extends Notification implements Serializable {
    private final int hitpoints;

    public HitpointNotification(String playerId, int hitpoints) {
        super(playerId);
        this.hitpoints = hitpoints;
    }

    public int getHitpoints() {
        return hitpoints;
    }

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
