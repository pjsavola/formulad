package gp.model;

import gp.LocalPlayer;

import java.io.Serializable;
import java.util.List;

public class Standings implements Serializable {
    private String[] playerIds;

    public Standings(List<LocalPlayer> allPlayers) {
        playerIds = new String[allPlayers.size()];
        for (int i = 0; i < allPlayers.size(); ++i) {
            playerIds[i] = allPlayers.get(i).getId();
        }
    }

    public String[] getPlayerIds() {
        return playerIds;
    }
}
