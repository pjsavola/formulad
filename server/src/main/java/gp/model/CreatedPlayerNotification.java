package gp.model;

import gp.Client;

import java.io.Serializable;
import java.util.List;

public class CreatedPlayerNotification extends Notification implements Serializable {
    private final String name;
    private final int nodeId;
    private final int hitpoints;
    private final int lapsToGo;
    private final int[] colors;
    private final double angle;
    private final Tires tires;
    private boolean isControlled;

    public CreatedPlayerNotification(String playerId, String name, int nodeId, int hitpoints, int lapsToGo, int[] colors, double angle, Tires tires) {
        super(playerId);
        this.name = name;
        this.nodeId = nodeId;
        this.hitpoints = hitpoints;
        this.lapsToGo = lapsToGo;
        this.colors = colors;
        this.angle = angle;
        this.tires = tires;
    }

    public String getName() {
        return name;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getHitpoints() {
        return hitpoints;
    }

    public int getLapsRemaining() {
        return lapsToGo;
    }

    public int[] getColors() {
        return colors;
    }

    public double getGridAngle() {
        return angle;
    }

    public Tires getTires() {
        return tires;
    }

    public boolean isControlled() {
        return isControlled;
    }

    public CreatedPlayerNotification controlled(boolean controlled) {
        this.isControlled = controlled;
        return this;
    }

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
