package formulad.model;

import formulad.Client;

import java.io.Serializable;

public class MovementNotification extends Notification implements Serializable {
    private final int nodeId;

    public MovementNotification(String playerId, int nodeId) {
        super(playerId);
        this.nodeId = nodeId;
    }

    public int getNodeId() {
        return nodeId;
    }

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
