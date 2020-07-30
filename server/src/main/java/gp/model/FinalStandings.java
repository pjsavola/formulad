package gp.model;

import java.io.Serializable;
import java.util.List;

public class FinalStandings implements Serializable {
    private final boolean isChampionshipRace;
    private PlayerStats[] finalStats;

    public FinalStandings(List<PlayerStats> stats, boolean season) {
        this.isChampionshipRace = season;
        finalStats = new PlayerStats[stats.size()];
        for (int i = 0; i < stats.size(); ++i) {
            finalStats[i] = stats.get(i);
        }
    }

    public boolean isSingleRace() {
        return !isChampionshipRace;
    }

    public PlayerStats[] getStats() {
        return finalStats;
    }
}
