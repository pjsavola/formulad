package formulad.model;

import java.io.Serializable;

public class MovementNotification implements Serializable {
    public String playerId;
    public int nodeId;

    public MovementNotification playerId(String playerId) {
        this.playerId = playerId;
        return this;
    }

    public MovementNotification nodeId(int nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getNodeId() {
        return nodeId;
    }
}
