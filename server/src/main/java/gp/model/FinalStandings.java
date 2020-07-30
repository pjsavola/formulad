package gp.model;

import java.io.Serializable;
import java.util.List;

public class FinalStandings implements Serializable {
    private final boolean season;
    private PlayerStats[] finalStats;

    public FinalStandings(List<PlayerStats> stats, boolean season) {
        this.season = season;
        finalStats = new PlayerStats[stats.size()];
        for (int i = 0; i < stats.size(); ++i) {
            finalStats[i] = stats.get(i);
        }
    }

    public boolean isSingleRace() {
        return !season;
    }

    public PlayerStats[] getStats() {
        return finalStats;
    }
}
