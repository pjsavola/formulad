package gp.ai;

import gp.Main;
import gp.model.*;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public abstract class BaseAI implements AI {

    String playerId;
    final TrackData data;
    final List<Node> nodes;

    public BaseAI(TrackData data) {
        this.data = data;
        nodes = data.getNodes();
    }

    @Override
    public void notify(Object notification) {
        if (notification instanceof CreatedPlayerNotification) {
            final CreatedPlayerNotification createdPlayer = (CreatedPlayerNotification) notification;
            if (createdPlayer.isControlled()) {
                if (playerId != null) {
                    Main.log.log(Level.SEVERE, "AI assigneed to control multiple players");
                }
                playerId = createdPlayer.getPlayerId();
            }
        }
    }
}
