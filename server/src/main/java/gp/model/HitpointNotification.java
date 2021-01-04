package gp.model;

import gp.Client;

import java.io.Serializable;

public class HitpointNotification extends Notification implements Serializable {
    public enum Source {COLLISION, CURVE, CRASH, ENGINE, GEARS, PITS }
    private final int hitpoints;
    private final Source source;

    public HitpointNotification(String playerId, int hitpoints, Source source) {
        super(playerId);
        this.hitpoints = hitpoints;
        this.source = source;
    }

    public int getHitpoints() {
        return hitpoints;
    }

    public Source getSource() {
        return source;
    }

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
