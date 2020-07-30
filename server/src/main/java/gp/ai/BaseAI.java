package gp.ai;

import gp.Main;
import gp.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

public abstract class BaseAI implements AI {

    String playerId;
    final TrackData data;

    public BaseAI(TrackData data) {
        this.data = data;
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
