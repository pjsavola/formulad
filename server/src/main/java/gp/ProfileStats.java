package gp;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileStats extends JDialog {
    final List<Profile.Result> results;
    private Predicate<Profile.Result> trackFilter;
    private Predicate<Profile.Result> typeFilter;
    private Predicate<Profile.Result> playerCountFilter = r -> r.standings != null && r.standings.size() == 10;
    private final JLabel startedRaces = new JLabel();
    private final JLabel completedRaces = new JLabel();
    private final JLabel dnfRaces = new JLabel();
    private final JLabel abortedRaces = new JLabel();
    private final JLabel completedLaps = new JLabel();
    private final JLabel timeUsed = new JLabel();
    private final JLabel wins = new JLabel();
    private final JLabel podiums = new JLabel();
    private final JLabel championshipPoints = new JLabel();

    ProfileStats(JFrame frame, Profile profile) {
        super(frame);
        setTitle(profile.getName() + " stats");
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        final Set<String> tracks = new TreeSet<>();
        final Map<String, String> trackNameToId = new HashMap<>();
        results = profile.getResults();
        for (final Profile.Result result : results) {
            final String track = result.trackId;
            final String firstLetter = Character.toString(track.charAt(0)).toUpperCase();
            final String trackName = firstLetter + track.substring(1, track.length() - 4).toLowerCase();
            tracks.add(trackName);
            trackNameToId.put(trackName, track);
        }
        updateStats();

        tracks.add("");
        final String[] trackArray = tracks.toArray(new String[0]);
        final JComboBox<String> trackFilter = new JComboBox<>(trackArray);
        trackFilter.addActionListener(e -> {
            int index = trackFilter.getSelectedIndex();
            if (index == 0) {
                this.trackFilter = null;
            } else {
                final String trackId = trackNameToId.get(trackArray[index]);
                this.trackFilter = r -> r.trackId.equals(trackId);
            }
            updateStats();
        });
        final String[] raceTypes = { "", "Single Races", "Championship Races" };
        final JComboBox<String> typeFilter = new JComboBox<>(raceTypes);
        typeFilter.addActionListener(e -> {
            int index = typeFilter.getSelectedIndex();
            if (index == 0) {
                this.typeFilter = null;
            } else {
                final boolean championshipRaces = "Championship Races".equals(raceTypes[index]);
                this.typeFilter = r -> r.isChampionshipRace == championshipRaces;
            }
            updateStats();
        });
        final Integer[] playerCounts = { 10, 9, 8, 7, 6, 5, 4, 3, 2, 1 };
        final JComboBox<Integer> playerFilter = new JComboBox<>(playerCounts);
        playerFilter.addActionListener(e -> {
            final int index = playerFilter.getSelectedIndex();
            final int playerCount = playerCounts[index];
            playerCountFilter = r -> r.standings != null && r.standings.size() == playerCount;
            updateStats();
        });

        final JPanel contents = new JPanel(new GridLayout(0, 3));
        contents.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        contents.add(new JLabel("Track filter"));
        contents.add(new JLabel("Race type filter"));
        contents.add(new JLabel("Player count filter"));
        contents.add(trackFilter);
        contents.add(typeFilter);
        contents.add(playerFilter);
        contents.add(new JLabel("Started Races:"));
        contents.add(startedRaces);
        contents.add(new JLabel());
        contents.add(new JLabel("Completed Races:"));
        contents.add(completedRaces);
        contents.add(new JLabel());
        contents.add(new JLabel("DNF Races:"));
        contents.add(dnfRaces);
        contents.add(new JLabel());
        contents.add(new JLabel("Aborted Races:"));
        contents.add(abortedRaces);
        contents.add(new JLabel());
        contents.add(new JLabel("Completed Laps:"));
        contents.add(completedLaps);
        contents.add(new JLabel());
        contents.add(new JLabel("Time Used:"));
        contents.add(timeUsed);
        contents.add(new JLabel());
        contents.add(new JLabel("Wins:"));
        contents.add(wins);
        contents.add(new JLabel());
        contents.add(new JLabel("Podiums:"));
        contents.add(podiums);
        contents.add(new JLabel());
        contents.add(new JLabel("Championship points:"));
        contents.add(championshipPoints);
        contents.add(new JLabel());
        setContentPane(contents);
        pack();
        setModal(true);
        setLocationRelativeTo(frame);
        setVisible(true);
    }

    private void updateStats() {
        final int[] posToPts = { 0, 10, 6, 4, 3, 2, 1, 0, 0, 0, 0 };
        Stream<Profile.Result> stream = results.stream();
        if (trackFilter != null) stream = stream.filter(trackFilter);
        if (typeFilter != null) stream = stream.filter(typeFilter);
        stream = stream.filter(playerCountFilter);
        final List<Profile.Result> filteredResults = stream.collect(Collectors.toList());
        startedRaces.setText(Integer.toString(filteredResults.size()));
        completedRaces.setText(Long.toString(filteredResults.stream().filter(Profile.Result::isComplete).count()));
        dnfRaces.setText(Long.toString(filteredResults.stream().filter(r -> r.remainingHitpoints <= 0).count()));
        abortedRaces.setText(Long.toString(filteredResults.stream().filter(r -> !r.isComplete()).count()));
        completedLaps.setText(Integer.toString(filteredResults.stream().mapToInt(r -> r.completedLaps).sum()));
        final String timeString;
        final long timeSeconds = filteredResults.stream().mapToLong(r -> r.timeUsedMs).sum() / 1000;
        final long timeMinutes = timeSeconds / 60;
        final long timeHours = timeSeconds / 3600;
        final long timeDays = timeSeconds / (3600 * 24);
        if (timeMinutes < 1) timeString = timeSeconds + " s";
        else if (timeHours < 1) timeString = timeMinutes + " min";
        else if (timeDays < 1) timeString = timeHours + " h " + (timeMinutes % 60) + " min";
        else timeString = timeDays + " d " + (timeHours % 24) + " h";
        timeUsed.setText(timeString);
        wins.setText(Long.toString(filteredResults.stream().filter(Profile.Result::isComplete).filter(r -> r.position == 1).count()));
        podiums.setText(Long.toString(filteredResults.stream().filter(Profile.Result::isComplete).filter(r -> r.position <= 3).count()));
        if (filteredResults.stream().noneMatch(Profile.Result::isChampionshipRace)) {
            championshipPoints.setText("");
        } else {
            championshipPoints.setText(Integer.toString(filteredResults.stream().filter(Profile.Result::isComplete).filter(Profile.Result::isChampionshipRace).mapToInt(r -> posToPts[r.position]).sum()));
        }
    }
}
