package formulad.model;

import formulad.LocalPlayer;

import java.io.Serializable;

public class PlayerStats implements Serializable {
    public String playerId;
    public int position;
    public int turns;
    public long timeUsed;
    public int exceptions;
    public int hitpoints;
    public double distance;
    public int gridPosition;

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
