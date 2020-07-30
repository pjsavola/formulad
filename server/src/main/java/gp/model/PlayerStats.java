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
        String result = playerId;
        result += "," + id;
        result += "," + position;
        result += "," + turns;
        result += "," + lapsToGo;
        result += "," + timeUsed;
        result += "," + exceptions;
        result += "," + hitpoints;
        result += "," + distance;
        result += "," + gridPosition;
        result += "," + pitStops;
        return result;
    }

    public static PlayerStats fromString(String str) {
        final PlayerStats stats = new PlayerStats();
        final String[] s = str.split(",");
        stats.playerId = s[0];
        stats.id = UUID.fromString(s[1]);
        stats.position = Integer.parseInt(s[2]);
        stats.turns = Integer.parseInt(s[3]);
        stats.lapsToGo = Integer.parseInt(s[4]);
        stats.timeUsed = Long.parseLong(s[5]);
        stats.exceptions = Integer.parseInt(s[6]);
        stats.hitpoints = Integer.parseInt(s[7]);
        stats.distance = Double.parseDouble(s[8]);
        stats.gridPosition = Integer.parseInt(s[9]);
        stats.pitStops = Integer.parseInt(s[10]);
        return stats;
    }
}
