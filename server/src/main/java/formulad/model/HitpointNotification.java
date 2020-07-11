package formulad.model;

import java.io.Serializable;

public class HitpointNotification implements Serializable {
    public String playerId;
    public int hitpoints;

    public HitpointNotification playerId(String playerId) {
        this.playerId = playerId;
        return this;
    }

    public HitpointNotification hitpoints(int hitpoints) {
        this.hitpoints = hitpoints;
        return this;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getHitpoints() {
        return hitpoints;
    }
}
