package formulad;

import formulad.model.FinalStandings;
import formulad.model.PlayerStats;
import formulad.model.Standings;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class Profile implements Serializable {
    private final UUID id;
    private String name;
    private int color1;
    private int color2;
    private List<Result> results = new ArrayList<>();

    private class Result implements Serializable {
        private final String trackId;
        private final int totalLaps;
        private final int totalHitpoints;
        private final int gridPosition;
        private boolean complete;
        private int position;
        private int turns;
        private int remainingHitpoints;
        private int completedLaps;
        private double coveredDistance;
        private long timeUsedMs;
        private List<UUID> standings;

        private Result(String trackId, int totalLaps, int totalHitpoints, int gridPosition) {
            this.trackId = trackId;
            this.totalLaps = totalLaps;
            this.totalHitpoints = totalHitpoints;
            this.gridPosition = gridPosition;
        }

        private void complete(int position, int turns, int remainingHitpoints, int completedLaps, double coveredDistance, long timeUsedMs, List<UUID> standings) {
            complete = true;
            this.position = position;
            this.remainingHitpoints = remainingHitpoints;
            this.completedLaps = completedLaps;
            this.coveredDistance = coveredDistance;
            this.timeUsedMs = timeUsedMs;
            this.standings = standings;
            if (position != standings.indexOf(id) + 1) {
                FormulaD.log.log(Level.WARNING, "Standings and position do not match");
            }
        }
    }

    public Profile(String name) {
        id = UUID.randomUUID();
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setColor1(int color1) {
        this.color1 = color1;
    }

    public int getColor1() {
        return color1;
    }

    public void setColor2(int color2) {
        this.color2 = color2;
    }

    public int getColor2() {
        return color2;
    }

    public void standingsReceived(PlayerStats[] standings, boolean initial) {
        final List<UUID> players = new ArrayList<>();
        PlayerStats myStats = null;
        for (PlayerStats stats : standings) {
            if (id.equals(stats.id)) {
                myStats = stats;
            }
            players.add(stats.id);
        }
        if (myStats != null) {
            if (initial) {
                results.add(new Result("sebring", myStats.lapsToGo, myStats.hitpoints, myStats.gridPosition));
            } else {
                final Result lastResult = results.get(results.size() - 1);
                if (!lastResult.complete) {
                    final int coveredLaps = lastResult.totalLaps - myStats.lapsToGo - 1;
                    lastResult.complete(myStats.position, myStats.turns, myStats.hitpoints, coveredLaps, myStats.distance, myStats.timeUsed, players);
                }
            }
        }
    }
}
