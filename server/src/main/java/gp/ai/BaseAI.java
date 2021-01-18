package gp.ai;

import gp.Main;
import gp.model.*;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public abstract class BaseAI implements AI {

    String playerId;
    int maxHitpoints;
    final TrackData data;
    final List<Node> nodes;
    int gear;

    BaseAI(TrackData data) {
        this.data = data;
        nodes = data.getNodes();
    }

    // This is called if AI takes over of a player after selecting a gear but before selecting where to move.
    public void init(GameState gameState, int gear) {
        selectGear(gameState);
        this.gear = gear;
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
            maxHitpoints = createdPlayer.getHitpoints();
        }
    }
}
