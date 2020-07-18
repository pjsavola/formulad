package formulad;

import formulad.model.FinalStandings;
import formulad.model.PlayerStats;
import formulad.model.Standings;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class Profile implements Serializable {
    private static final long serialVersionUID = -299482035708790405L;

    private final UUID id;
    private String name;
    private int color1;
    private int color2;
    private boolean active;
    private List<Result> results = new ArrayList<>();

    private static class Result implements Serializable {
        private static final long serialVersionUID = -299482035708790406L;
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

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public void standingsReceived(PlayerStats[] standings, String trackId) {
        final List<UUID> players = new ArrayList<>();
        PlayerStats myStats = null;
        for (PlayerStats stats : standings) {
            if (id.equals(stats.id)) {
                myStats = stats;
            }
            players.add(stats.id);
        }
        if (myStats != null) {
            if (trackId != null) {
                results.add(new Result(trackId, myStats.lapsToGo, myStats.hitpoints, myStats.gridPosition));
            } else {
                final Result lastResult = results.get(results.size() - 1);
                if (!lastResult.complete) {
                    final int coveredLaps = lastResult.totalLaps - myStats.lapsToGo - 1;
                    lastResult.complete(myStats.position, myStats.turns, myStats.hitpoints, coveredLaps, myStats.distance, myStats.timeUsed, players);
                    if (myStats.position != players.indexOf(id) + 1) {
                        FormulaD.log.log(Level.WARNING, "Standings and position do not match");
                    }
                }
            }
        }
    }

    public String getLastTrack() {
        final String trackId;
        if (!results.isEmpty()) {
            trackId = results.get(results.size() - 1).trackId;
            try {
                if (FormulaD.validateTrack(trackId)) {
                    return trackId;
                }
            } catch (Exception e) {
                // TODO: Better exception handling
            }
        }
        return "sebring.dat";
    }
}
