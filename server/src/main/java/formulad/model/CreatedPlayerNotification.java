package formulad.model;

import formulad.Client;

import java.io.Serializable;

public class CreatedPlayerNotification extends Notification implements Serializable {
    private final String name;
    private final int nodeId;
    private final int hitpoints;
    private final int lapsToGo;

    public CreatedPlayerNotification(String playerId, String name, int nodeId, int hitpoints, int lapsToGo) {
        super(playerId);
        this.name = name;
        this.nodeId = nodeId;
        this.hitpoints = hitpoints;
        this.lapsToGo = lapsToGo;
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

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
