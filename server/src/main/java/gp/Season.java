package gp;

import gp.ai.TrackData;
import gp.model.FinalStandings;
import gp.model.PlayerStats;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Season implements Comparable<Season>, TrackSelector {
    private final String name;
    private long timeStamp;
    private int animationDelayMs;
    private int timePerTurnMs;
    private int leewayMs;
    private List<Pair<TrackData, Integer>> tracksAndLaps = new ArrayList<>();
    private List<ProfileMessage> participants = new ArrayList<>();
    private List<FinalStandings> results = new ArrayList<>();

    // For UI
    private List<TrackButton> tracks = new ArrayList<>();
    private JPanel buttonPanel;
    private final JFrame frame;

    Season(JFrame frame, String name) {
        this.frame = frame;
        this.name = name;
    }

    String getName() {
        return name;
    }

    void start(List<Profile> profiles, WindowChanger listener) {
        final JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0, 2));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        final JPanel tracksPanel = new JPanel();
        final JPanel rightPanel = new JPanel();
        tracksPanel.setLayout(new BoxLayout(tracksPanel, BoxLayout.Y_AXIS));
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        panel.add(tracksPanel);
        panel.add(rightPanel);
        buttonPanel = new JPanel(new GridLayout(0, 2));
        final List<String> internal = new ArrayList<>();
        final List<String> external = new ArrayList<>();
        TrackPreviewButton.getAllTracks(internal, external);
        final JButton addTrackButton = new JButton("Add track...");
        addTrackButton.addActionListener(a -> {
            TrackPreviewButton.openTrackSelectionDialog(frame, this);
        });
        buttonPanel.add(addTrackButton);
        internal.stream().map(f -> TrackData.createTrackData(f, false)).filter(Objects::nonNull).forEach(data -> setTrack(data, null));
        final List<ProfileMessage> localProfiles = profiles.stream().map(ProfileMessage::new).collect(Collectors.toList());
        final JPanel playerPanel = new JPanel(new GridLayout(5, 2));
        final PlayerSlot[] slots = new PlayerSlot[10];
        for (int i = 0; i < slots.length; ++i) {
            final PlayerSlot slot = new PlayerSlot(frame, localProfiles, null, slots, i + 1) {
                @Override
                public String getText() {
                    return profile == null ? "Free" : profile.getName();
                }
            };
            playerPanel.add(slot);
            slots[i] = slot;
        }
        final JLabel tracksLabel = new JLabel("TRACKS");
        tracksLabel.setFont(new Font("Arial", Font.BOLD, 20));
        tracksPanel.add(tracksLabel);
        tracksPanel.add(buttonPanel);

        final JLabel playersLabel = new JLabel("PLAYERS");
        playersLabel.setFont(new Font("Arial", Font.BOLD, 20));
        final JCheckBox randomTrackOrder = new JCheckBox("Randomize track order", false);
        rightPanel.add(playersLabel);
        rightPanel.add(playerPanel);
        rightPanel.add(randomTrackOrder);
        final SettingsField animationDelay = new SettingsField(panel, "Animation delay (ms)", Integer.toString(Main.settings.animationDelay), 0, 1000);
        final SettingsField time = new SettingsField(panel, "Time per turn (s)", Integer.toString(Main.settings.timePerTurn), 0, 3600);
        final SettingsField leeway = new SettingsField(panel, "Time leeway (s)", Integer.toString(Main.settings.leeway), 0, 36000);
        rightPanel.add(animationDelay);
        rightPanel.add(time);
        rightPanel.add(leeway);
        final JButton startButton = new JButton("Start");
        rightPanel.add(startButton);
        startButton.addActionListener(a -> {
            final Set<UUID> ids = new HashSet<>();
            for (PlayerSlot slot : slots) {
                final ProfileMessage profile = slot.getProfile();
                if (profile != null) {
                    if (profile == ProfileMessage.pending) {
                        JOptionPane.showConfirmDialog(frame, "Incomplete participant", "Error", JOptionPane.DEFAULT_OPTION);
                        return;
                    }
                    else if (!profile.isAi() && !ids.add(profile.getId())) {
                        JOptionPane.showConfirmDialog(frame, "Duplicate profile: " + profile.getName(), "Error", JOptionPane.DEFAULT_OPTION);
                        return;
                    }
                    participants.add(profile);
                }
            }
            if (participants.size() < 6) {
                JOptionPane.showConfirmDialog(frame, "Need at least 6 participants", "Error", JOptionPane.DEFAULT_OPTION);
                return;
            }
            try {
                animationDelayMs = animationDelay.getValue();
                timePerTurnMs = time.getValue() * 1000;
                leewayMs = leeway.getValue() * 1000;
            } catch (NumberFormatException ex) {
                return;
            }
            if (randomTrackOrder.isSelected()) {
                Collections.shuffle(tracks);
            }
            timeStamp = System.currentTimeMillis();
            for (TrackButton button : tracks) {
                tracksAndLaps.add(Pair.of(button.data, button.laps.getValue()));
            }
            save();
            showStandings(listener);
        });
        listener.contentChanged(panel, null, null, "Setting up season " + name, false);
        frame.setContentPane(panel);
        frame.pack();
    }

    private class CarIcon implements Icon {
        private final Color color1;
        private final Color color2;
        private CarIcon(int color1, int color2) {
            this.color1 = new Color(color1);
            this.color2 = new Color(color2);
        }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Player.draw((Graphics2D) g.create(), x + 16, y + 9, 0, color1, color2, 2.0);
        }
        @Override
        public int getIconWidth() {
            return 32;
        }
        @Override
        public int getIconHeight() {
            return 18;
        }
    }

    private static final int[] pointDistribution = { 10, 6, 4, 3, 2, 1, 0, 0, 0, 0 };

    void showStandings(WindowChanger listener) {
        final JPanel masterPanel = new JPanel();
        masterPanel.setLayout(new BoxLayout(masterPanel, BoxLayout.Y_AXIS));
        final JPanel panel = new JPanel(new GridLayout(0, 3));
        panel.setBorder(new EmptyBorder(20, 0, 20, 80));
        final Map<UUID, List<Integer>> points = new HashMap<>();
        final Map<UUID, Integer> totalPoints = new HashMap<>();
        participants.forEach(participant -> totalPoints.put(participant.getId(), 0));
        for (FinalStandings result : results) {
            for (PlayerStats stats :result.getStats()) {
                points.computeIfAbsent(stats.id, id -> new ArrayList<>()).add(pointDistribution[stats.position - 1]);
            }
        }
        points.forEach((id, pointResults) -> totalPoints.put(id, pointResults.stream().mapToInt(Integer::intValue).sum()));
        final List<ProfileMessage> sortedParticipants = participants.stream().sorted((p1, p2) -> totalPoints.get(p2.getId()) - totalPoints.get(p1.getId())).collect(Collectors.toList());
        // Header
        panel.add(new JLabel());
        panel.add(new JLabel());
        final JPanel trackPanel = new JPanel(new GridLayout(0, tracksAndLaps.size() + 1));
        trackPanel.add(new JLabel());
        for (int i = 0; i < tracksAndLaps.size(); ++i) {
            final JLabel trackInfo = new JLabel(Integer.toString(i + 1));
            trackPanel.add(trackInfo);
            // TODO: Draw track icon + reseult info box when clicking the label
        }
        panel.add(trackPanel);
        for (int rank = 0; rank < sortedParticipants.size(); ++rank) {
            final ProfileMessage player = sortedParticipants.get(rank);
            final JLabel pos = new JLabel((rank + 1) + ".");
            final JLabel label = new JLabel(player.getName());
            final JPanel ptsTable = new JPanel(new GridLayout(0, tracksAndLaps.size() + 1));
            final JLabel pts = new JLabel(Integer.toString(totalPoints.get(player.getId())));
            pos.setFont(new Font("Arial", Font.BOLD, 20));
            label.setFont(new Font("Arial", Font.BOLD, 20));
            pts.setFont(new Font("Arial", Font.BOLD, 20));
            label.setIcon(new CarIcon(player.getColor1(), player.getColor2()));
            label.setIconTextGap(20);
            pos.setHorizontalAlignment(SwingConstants.RIGHT);
            label.setBorder(new EmptyBorder(0, 0, 0, 10));
            pos.setBorder(new EmptyBorder(0, 0, 0, 10));
            pts.setBorder(new EmptyBorder(0, 0, 0, 10));
            panel.add(pos);
            panel.add(label);
            panel.add(ptsTable);
            ptsTable.add(pts);
            for (int i = 0; i < tracksAndLaps.size(); ++i) {
                if (results.size() > i) {
                    ptsTable.add(new JLabel(Integer.toString(points.get(player.getId()).get(i))));
                } else {
                    ptsTable.add(new JLabel("-"));
                }
            }
        }
        masterPanel.add(panel);
        final JButton continueButton = new JButton("Next Race");
        continueButton.addActionListener(a -> {
            final TrackData data = tracksAndLaps.get(results.size()).getLeft();
            final int laps = tracksAndLaps.get(results.size()).getRight();
            final PlayerSlot[] slots = new PlayerSlot[sortedParticipants.size()];
            for (int i = 0; i < slots.length; ++i) {
                slots[i] = new PlayerSlot(sortedParticipants.get(i), i + 1);
            }
            final Main server = new Main(new Main.Params(laps, animationDelayMs, timePerTurnMs, leewayMs), null, frame, masterPanel, slots, data);
            server.storeResultsTo(results);
            listener.contentChanged(server, null, server, "championship race", true);
            Main.setContent(frame, server);
            new Thread(server).start();
        });
        final boolean complete = results.size() == tracksAndLaps.size();
        if (!complete) {
            masterPanel.add(continueButton);
        }
        listener.contentChanged(masterPanel, null, null, "Season " + name, false);
        frame.setContentPane(masterPanel);
        frame.pack();
    }

    boolean load() {
        try (InputStreamReader ir = new InputStreamReader(new FileInputStream(name + ".cha")); final BufferedReader br = new BufferedReader(ir)) {
            int phase = 0;
            String line;
            final List<PlayerStats> stats = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    ++phase;
                    if (phase >= 3) {
                        if (!stats.isEmpty()) {
                            results.add(new FinalStandings(stats));
                        }
                        stats.clear();
                    }
                } else if (phase < 3) {
                    final String[] parts = line.split(",");
                    switch (phase) {
                        case 0:
                            timeStamp = Long.parseLong(parts[1]);
                            animationDelayMs = Integer.parseInt(parts[2]);
                            timePerTurnMs = Integer.parseInt(parts[3]);
                            leewayMs = Integer.parseInt(parts[4]);
                            break;
                        case 1:
                            final TrackData data = TrackData.createTrackData(parts[0], Boolean.parseBoolean(parts[1]));
                            if (data == null) {
                                Main.log.log(Level.SEVERE, "Failed to load Championship Season " + name + ". Loading of " + parts[0] + " failed");
                                return false;
                            }
                            tracksAndLaps.add(Pair.of(data, Integer.parseInt(parts[2])));
                            break;
                        case 2:
                            participants.add(ProfileMessage.readProfile(parts));
                            break;
                    }
                } else {
                    stats.add(PlayerStats.fromString(line));
                }
            }
        } catch (Exception e) {
            Main.log.log(Level.SEVERE, "Failed to load Championship Season " + name, e);
            return false;
        }
        return true;
    }

    void save() {
        try (final PrintWriter writer = new PrintWriter(name + ".cha", "UTF-8")) {
            writer.print(name);
            writer.print(",");
            writer.print(timeStamp);
            writer.print(",");
            writer.print(animationDelayMs);
            writer.print(",");
            writer.print(timePerTurnMs);
            writer.print(",");
            writer.println(leewayMs);
            writer.println();
            for (Pair<TrackData, Integer> p : tracksAndLaps) {
                writer.print(p.getLeft().getTrackId());
                writer.print(",");
                writer.print(p.getLeft().isExternal());
                writer.print(",");
                writer.println(p.getRight());
            }
            writer.println();
            for (ProfileMessage message : participants) {
                writer.print(message.getId());
                writer.print(",");
                writer.print(message.getName());
                writer.print(",");
                writer.print(message.getColor1());
                writer.print(",");
                writer.print(message.getColor2());
                writer.print(",");
                if (message.isAi()) {
                    writer.println(message.isAi() ? "gp.ai.GreatAI" : "gp.ai.ManualAI");
                }
            }
            writer.println();
            for (FinalStandings fs : results) {
                for (PlayerStats stats : fs.getStats()) {
                    writer.println(stats.toString());
                }
                writer.println();
            }
        } catch (Exception e) {
            Main.log.log(Level.SEVERE, "Failed to save Championship Season " + name, e);
        }
    }

    boolean delete() {
        final File file = new File(name + ".cha");
        return file.exists() && file.delete();
    }

    @Override
    public int compareTo(Season season) {
        return (int) ((season.timeStamp - timeStamp) / 1000);
    }

    private static class TrackButton extends JButton {
        private final TrackData data;
        private final SettingsField laps;
        private TrackButton(TrackData data, SettingsField laps) {
            final String id = data.getTrackId();
            final String name = StringUtils.capitalize(id.substring(0, id.length() - 4));
            setText(name);
            this.data = data;
            this.laps = laps;
        }
    }

    @Override
    public void setTrack(TrackData data, ImageIcon icon) {
        final SettingsField laps = new SettingsField(buttonPanel, "Laps", Integer.toString(Main.settings.laps), 1, 200);
        final TrackButton button = new TrackButton(data, laps);
        final int count = buttonPanel.getComponentCount();
        buttonPanel.add(button, count - 1);
        buttonPanel.add(laps, count);
        frame.pack();
        button.addActionListener(a -> {
            if (tracks.size() < 4) return;
            buttonPanel.remove(button);
            buttonPanel.remove(laps);
            frame.pack();
            tracks.remove(button);
        });
        tracks.add(button);
    }
}
