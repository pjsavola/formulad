package gp.model;

import gp.Client;

import java.io.Serializable;

public class CreatedPlayerNotification extends Notification implements Serializable {
    private final String name;
    private final int nodeId;
    private final int hitpoints;
    private final int lapsToGo;
    private final int color1;
    private final int color2;
    private final double angle;

    public CreatedPlayerNotification(String playerId, String name, int nodeId, int hitpoints, int lapsToGo, int color1, int color2, double angle) {
        super(playerId);
        this.name = name;
        this.nodeId = nodeId;
        this.hitpoints = hitpoints;
        this.lapsToGo = lapsToGo;
        this.color1 = color1;
        this.color2 = color2;
        this.angle = angle;
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

    public int getColor1() {
        return color1;
    }

    public int getColor2() {
        return color2;
    }

    public double getGridAngle() {
        return angle;
    }

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
