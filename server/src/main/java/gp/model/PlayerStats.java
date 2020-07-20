package gp.model;

import java.io.Serializable;
import java.util.UUID;

public class PlayerStats implements Serializable {
    public String playerId;
    public UUID id;
    public int position;
    public int turns;
    public int lapsToGo;
    public long timeUsed;
    public int exceptions;
    public int hitpoints;
    public double distance;
    public int gridPosition;
    public int pitStops;

    @Override
    public String toString() {
        final String stats = "Turns: " + turns + " Time: " + timeUsed + " Exceptions: " + exceptions;
        if (hitpoints > 0) {
            return stats + " Hitpoints: " + hitpoints + " -- FINISHED!";
        } else {
            return stats + " -- Distance: " + distance;
        }
    }
}
