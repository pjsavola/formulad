package gp;

import gp.model.PlayerStats;

import java.io.*;
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
    private int color3;
    private int color4;
    private boolean active;
    private List<Result> results = new ArrayList<>();
    private transient Manager manager;

    public static class Manager {
        private final List<Profile> profiles = new ArrayList<>();

        public void saveProfiles() {
            try {
                final FileOutputStream fos = new FileOutputStream("profiles.sav");
                final ObjectOutputStream oos = new ObjectOutputStream(fos);
                for (Profile profile : profiles) {
                    oos.writeObject(profile);
                }
            } catch (IOException ex) {
                Main.log.log(Level.SEVERE, "Writing of profiles.sav failed");
            }
        }
    }

    static class Result implements Serializable {
        private static final long serialVersionUID = -299482035708790406L;
        final String trackId;
        final int totalLaps;
        final int totalHitpoints;
        final int gridPosition;
        private boolean complete;
        int position;
        int turns;
        int remainingHitpoints;
        int completedLaps;
        private double coveredDistance;
        long timeUsedMs;
        List<UUID> standings;
        boolean isChampionshipRace;
        boolean weather;

        private Result(String trackId, int totalLaps, int totalHitpoints, int gridPosition, boolean isSingleRace, boolean weather) {
            this.trackId = trackId;
            this.totalLaps = totalLaps;
            this.totalHitpoints = totalHitpoints;
            this.gridPosition = gridPosition;
            this.isChampionshipRace = !isSingleRace;
            this.weather = weather;
        }

        private void complete(int position, int turns, int remainingHitpoints, int completedLaps, double coveredDistance, long timeUsedMs, List<UUID> standings) {
            complete = true;
            this.position = position;
            this.turns = turns;
            this.remainingHitpoints = remainingHitpoints;
            this.completedLaps = completedLaps;
            this.coveredDistance = coveredDistance;
            this.timeUsedMs = timeUsedMs;
            this.standings = standings;
        }

        boolean isComplete() {
            return complete;
        }

        boolean isChampionshipRace() {
            return isChampionshipRace;
        }
    }

    Profile(Manager manager, String name) {
        id = UUID.randomUUID();
        this.name = name;
        this.manager = manager;
        manager.profiles.add(this);
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

    void setColor(int index, int color) {
        switch (index) {
            case 0: color1 = color; break;
            case 1: color2 = color; break;
            case 2: color3 = color; break;
            case 3: color4 = color; break;
            default: throw new RuntimeException("Invalid color index");
        }
    }

    int getColor(int index) {
        switch (index) {
            case 0: return color1;
            case 1: return color2;
            case 2: return color3;
            case 3: return color4;
            default: throw new RuntimeException("Invalid color index");
        }
    }

    int[] getColors() {
        return new int[] { color1, color2, color3, color4 };
    }

    void setActive(boolean active) {
        this.active = active;
    }

    boolean isActive() {
        return active;
    }

    List<Result> getResults() {
        return results;
    }

    void setManager(Profile.Manager manager) {
        this.manager = manager;
        manager.profiles.add(this);
    }

    void delete() {
        manager.profiles.remove(this);
    }

    public Manager getManager() {
        return manager;
    }

    public void standingsReceived(PlayerStats[] standings, String trackId, boolean isSingleRace, boolean weather) {
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
                results.add(new Result(trackId, myStats.lapsToGo, myStats.hitpoints, myStats.gridPosition, isSingleRace, weather));
            } else {
                final Result lastResult = results.get(results.size() - 1);
                if (!lastResult.complete) {
                    final int coveredLaps = lastResult.totalLaps - myStats.lapsToGo - 1;
                    lastResult.complete(myStats.position, myStats.turns, myStats.hitpoints, coveredLaps, myStats.distance, myStats.timeUsed, players);
                    if (myStats.position != players.indexOf(id) + 1) {
                        Main.log.log(Level.WARNING, "Standings and position do not match");
                    }
                }
            }
        }
    }
}
