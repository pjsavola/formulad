package gp;


import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ProfileStats extends JDialog {
    private final List<Profile.Result> results;
    private Predicate<Profile.Result> trackFilter;
    private Predicate<Profile.Result> typeFilter;
    private Predicate<Profile.Result> playerCountFilter = r -> r.standings != null;
    private Predicate<Profile.Result> lapCountFilter;
    private final JLabel completedRaces = new JLabel();
    private final JLabel dnfRaces = new JLabel();
    private final JLabel completedLaps = new JLabel();
    private final JLabel wins = new JLabel();
    private final JLabel podiums = new JLabel();
    private final JLabel championshipPoints = new JLabel();
    private final JLabel bestResult = new JLabel();

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
        final String[] playerCounts = { "1-10", "2-10", "3-10", "4-10", "5-10", "6-10", "7-10", "8-10", "9-10", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" };
        final JComboBox<String> playerFilter = new JComboBox<>(playerCounts);
        playerFilter.addActionListener(e -> {
            final int index = playerFilter.getSelectedIndex();
            final String[] interval = playerCounts[index].split("-");
            final int min = Integer.parseInt(interval[0]);
            final int max = interval.length > 1 ? Integer.parseInt(interval[1]) : min;
            playerCountFilter = r -> r.standings != null && r.standings.size() >= min && r.standings.size() <= max;
            updateStats();
        });
        final JTextField lapFilter = new JTextField();
        lapFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }
            private void update() {
                try {
                    final int value = Integer.parseInt(lapFilter.getText());
                    lapCountFilter = r -> r.totalLaps == value;
                } catch (NumberFormatException e) {
                    lapCountFilter = null;
                }
                updateStats();
            }
        });

        final JPanel contents = new JPanel(new GridLayout(0, 2));
        contents.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        contents.add(new JLabel("Total Races:"));
        contents.add(new JLabel(Integer.toString(results.size())));
        contents.add(new JLabel("Aborted Races:"));
        contents.add(new JLabel(Long.toString(results.stream().filter(r -> !r.isComplete()).count())));

        final String timeString;
        final long timeSeconds = results.stream().mapToLong(r -> r.timeUsedMs).sum() / 1000;
        final long timeMinutes = timeSeconds / 60;
        final long timeHours = timeSeconds / 3600;
        final long timeDays = timeSeconds / (3600 * 24);
        if (timeMinutes < 1) timeString = timeSeconds + " s";
        else if (timeHours < 1) timeString = timeMinutes + " min";
        else if (timeDays < 1) timeString = timeHours + " h " + (timeMinutes % 60) + " min";
        else timeString = timeDays + " d " + (timeHours % 24) + " h";
        contents.add(new JLabel("Time used:"));
        contents.add(new JLabel(timeString));
        contents.add(new JLabel());
        contents.add(new JLabel());
        contents.add(new JLabel("Track filter"));
        contents.add(trackFilter);
        contents.add(new JLabel("Race type filter"));
        contents.add(typeFilter);
        contents.add(new JLabel("Player count filter"));
        contents.add(playerFilter);
        contents.add(new JLabel("Lap count filter"));
        contents.add(lapFilter);
        contents.add(new JLabel("Completed Races:"));
        contents.add(completedRaces);
        contents.add(new JLabel("DNF Races:"));
        contents.add(dnfRaces);
        contents.add(new JLabel("Completed Laps:"));
        contents.add(completedLaps);
        contents.add(new JLabel("Wins:"));
        contents.add(wins);
        contents.add(new JLabel("Podiums:"));
        contents.add(podiums);
        contents.add(new JLabel("Championship points:"));
        contents.add(championshipPoints);
        contents.add(new JLabel("Quickest finish in turns:"));
        contents.add(bestResult);
        setContentPane(contents);
        pack();
        setModal(true);
        setLocationRelativeTo(frame);
        setVisible(true);
    }

    private void updateStats() {
        Stream<Profile.Result> stream = results.stream().filter(Profile.Result::isComplete);
        if (trackFilter != null) stream = stream.filter(trackFilter);
        if (typeFilter != null) stream = stream.filter(typeFilter);
        if (lapCountFilter != null) stream = stream.filter(lapCountFilter);
        stream = stream.filter(playerCountFilter);
        final List<Profile.Result> filteredResults = stream.collect(Collectors.toList());
        completedRaces.setText(Long.toString(filteredResults.stream().filter(Profile.Result::isComplete).count()));
        dnfRaces.setText(Long.toString(filteredResults.stream().filter(r -> r.remainingHitpoints <= 0).count()));
        completedLaps.setText(Integer.toString(filteredResults.stream().mapToInt(r -> r.completedLaps).sum()));
        wins.setText(Long.toString(filteredResults.stream().filter(Profile.Result::isComplete).filter(r -> r.position == 1).count()));
        podiums.setText(Long.toString(filteredResults.stream().filter(Profile.Result::isComplete).filter(r -> r.position <= 3).count()));
        if (filteredResults.stream().noneMatch(Profile.Result::isChampionshipRace)) {
            championshipPoints.setText("");
        } else {
            championshipPoints.setText(Integer.toString(filteredResults.stream().filter(Profile.Result::isComplete).filter(Profile.Result::isChampionshipRace).mapToInt(r -> Season.pointDistribution[r.position - 1]).sum()));
        }
        final int minTurns = filteredResults.stream().filter(r -> r.remainingHitpoints > 0).filter(r -> r.turns > 0).mapToInt(r -> r.turns).min().orElse(0);
        bestResult.setText(minTurns > 0 ? Integer.toString(minTurns) : "");
    }
}
