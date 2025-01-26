package gp;

import gp.ai.Node;
import gp.ai.TrackData;
import gp.model.FinalStandings;
import gp.model.PlayerStats;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Season implements Comparable<Season>, TrackSelector {
    private final String name;
    private long timeStamp;
    private int animationDelayMs;
    private int timePerTurnMs;
    private int leewayMs;
    private int maxHitpoints;
    private boolean tireChanges;
    private List<Pair<TrackData, Integer>> tracksAndLaps = new ArrayList<>();
    private List<ProfileMessage> participants = new ArrayList<>();
    private List<FinalStandings> results = new ArrayList<>();
    private List<ProfileMessage> sortedParticipants;
    private int[] pointDistribution = defaultPointDistribution;
    private static final int[] defaultPointDistribution = { 10, 6, 4, 3, 2, 1 };

    // For UI
    private List<TrackButton> tracks = new ArrayList<>();
    private JPanel buttonPanel;
    private final JFrame frame;
    private JPanel masterPanel;
    private JButton continueButton;
    private JPanel playerPanel;
    private List<PlayerSlot> slots;
    private List<ProfileMessage> localProfiles;

    Season(JFrame frame, String name) {
        this.frame = frame;
        this.name = name;
    }

    class PositionData {
        int position;
        boolean dnf;

        PositionData(int position, boolean dnf) {
            this.position = position;
            this.dnf = dnf;
        }
    }

    private int getPoints(int position) {
        return position > pointDistribution.length ? 0 : pointDistribution[position - 1];
    }

    static int getDefaultPoints(int position) {
        return position > defaultPointDistribution.length ? 0 : defaultPointDistribution[position - 1];
    }

    String getName() {
        return name;
    }

    private int getPlayerCount() {
        return tracks.stream().map(b -> b.data).mapToInt(TrackData::getGridMaxSize).min().orElse(Main.minGridSize);
    }

    void start(List<Profile> profiles, List<Pair<String, Integer>> trackIds, List<ProfileMessage> profileMessages, WindowChanger listener) {
        final JPanel panel = new JPanel();
        slots = new ArrayList<>();
        panel.setLayout(new GridLayout(0, 2));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        final JPanel tracksPanel = new JPanel();
        final JPanel rightPanel = new JPanel();
        tracksPanel.setLayout(new BoxLayout(tracksPanel, BoxLayout.Y_AXIS));
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        panel.add(tracksPanel);
        panel.add(rightPanel);
        buttonPanel = new JPanel(new GridLayout(0, 2));
        final JButton addTrackButton = new JButton("Add track...");
        addTrackButton.addActionListener(a -> TrackPreviewButton.openTrackSelectionDialog(frame, this, TrackPreviewButton.getRequiredGridSize(slots), null));
        buttonPanel.add(addTrackButton);
        if (trackIds != null) {
            trackIds.parallelStream().map(f -> {
                final boolean external = !f.getLeft().startsWith("/");
                final TrackData data = TrackData.createTrackData(f.getLeft().substring(external ? 0 : 1), external);
                return data == null ? null : Pair.of(data, f.getRight());
            }).filter(Objects::nonNull).collect(Collectors.toList()).forEach(f -> {
                setTrack(f.getLeft(), null);
                tracks.get(tracks.size() - 1).laps.setValue(f.getRight());
            });
        }
        if (tracks.size() < 3) {
            final List<String> internal = new ArrayList<>();
            final List<String> external = new ArrayList<>();
            TrackPreviewButton.getAllTracks(internal, external);
            internal.parallelStream().map(f -> TrackData.createTrackData(f, false)).filter(Objects::nonNull).collect(Collectors.toList()).forEach(data -> {
                setTrack(data, null);
                tracks.get(tracks.size() - 1).laps.setValue(Main.settings.laps);
            });
        }
        localProfiles = profiles.stream().map(ProfileMessage::new).collect(Collectors.toList());
        playerPanel = new JPanel(new GridLayout(5, 2));
        final int playerCount = getPlayerCount();
        for (int i = 0; i < playerCount; ++i) {
            final PlayerSlot slot = new PlayerSlot(frame, localProfiles, null, slots, i + 1) {
                @Override
                public String getText() {
                    return profile == null ? "Free" : profile.getName();
                }
            };
            playerPanel.add(slot);
            slots.add(slot);
        }
        if (profileMessages != null) {
            int i = 0;
            while (i < slots.size() && i < profileMessages.size()) {
                final ProfileMessage msg = profileMessages.get(i);
                final ProfileMessage localProfile = localProfiles.stream().filter(p -> p.getName().equals(msg.getName())).findFirst().orElse(null);
                if (localProfile != null) {
                    localProfiles.remove(localProfile);
                    msg.setLocal();
                }
                slots.get(i).setProfile(msg);
                ++i;
            }
        }
        final JLabel tracksLabel = new JLabel("TRACKS");
        tracksLabel.setFont(new Font("Arial", Font.BOLD, 20));
        tracksPanel.add(tracksLabel);
        tracksPanel.add(buttonPanel);

        final JLabel playersLabel = new JLabel("PLAYERS");
        playersLabel.setFont(new Font("Arial", Font.BOLD, 20));
        final JCheckBox randomTrackOrder = new JCheckBox("Randomize track order", false);
        final JCheckBox tireChanges = new JCheckBox("Enable weather rules", Main.settings.tireChanges);
        rightPanel.add(playersLabel);
        rightPanel.add(playerPanel);
        rightPanel.add(randomTrackOrder);
        rightPanel.add(tireChanges);
        final SettingsField hitpoints = new SettingsField(panel, "Hitpoints", Integer.toString(Main.settings.maxHitpoints), SettingsField.minHP, SettingsField.maxHP);
        rightPanel.add(hitpoints);
        final PointDistributionField pointDistributionField = new PointDistributionField(panel, "Point distribution", defaultPointDistribution);
        rightPanel.add(pointDistributionField);
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
                        participants.clear();
                        return;
                    }
                    else if (!profile.isAi() && !ids.add(profile.getId())) {
                        JOptionPane.showConfirmDialog(frame, "Duplicate profile: " + profile.getName(), "Error", JOptionPane.DEFAULT_OPTION);
                        participants.clear();
                        return;
                    }
                    participants.add(profile);
                }
            }
            if (participants.size() < Main.minGridSize) {
                JOptionPane.showConfirmDialog(frame, "Need at least " + Main.minGridSize + " participants", "Error", JOptionPane.DEFAULT_OPTION);
                participants.clear();
                return;
            }
            try {
                animationDelayMs = animationDelay.getValue();
                timePerTurnMs = time.getValue() * 1000;
                leewayMs = leeway.getValue() * 1000;
                maxHitpoints = hitpoints.getValue();
                pointDistribution = pointDistributionField.getValue();
                this.tireChanges = tireChanges.isSelected();
            } catch (NumberFormatException ex) {
                participants.clear();
                return;
            }
            if (randomTrackOrder.isSelected()) {
                Collections.shuffle(tracks);
            }
            for (TrackButton button : tracks) {
                tracksAndLaps.add(Pair.of(button.data, button.laps.getValue()));
            }
            Collections.shuffle(participants);
            save();
            showStandings(listener);
        });
        listener.contentChanged(panel, null, null, "Setting up season " + name, false);
        frame.setContentPane(panel);
        frame.pack();
    }

    private class CarIcon implements Icon {
        private final int[] colors;
        private CarIcon(int[] colors) {
            this.colors = colors;
        }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Player.draw((Graphics2D) g.create(), x + 16, y + 9, 0, colors, 2.0);
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

    private int compare(UUID id1, UUID id2, Map<UUID, List<PositionData>> positions) {
        List<PositionData> pos1 = positions.get(id1);
        List<PositionData> pos2 = positions.get(id2);
    
        // Handle cases where one or both players have no positions
        if (pos1 == null && pos2 == null) return 0;
        if (pos1 == null) return 1;
        if (pos2 == null) return -1;
    
        // Calculate total points for each player
        int totalPoints1 = pos1.stream().mapToInt(pd -> pd.dnf ? 0 : getPoints(pd.position)).sum();
        int totalPoints2 = pos2.stream().mapToInt(pd -> pd.dnf ? 0 : getPoints(pd.position)).sum();
    
        // Compare total points
        return Integer.compare(totalPoints2, totalPoints1); // Descending order
    }

    private JPanel createStandingsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c1 = new GridBagConstraints();
        c1.fill = GridBagConstraints.HORIZONTAL;
        c1.gridx = 0;
        c1.gridy = 0;
        c1.weightx = 0.05;
        final GridBagConstraints c2 = new GridBagConstraints();
        c2.fill = GridBagConstraints.HORIZONTAL;
        c2.gridx = 1;
        c2.gridy = 0;
        c2.weightx = 0.40;
        final GridBagConstraints c3 = new GridBagConstraints();
        c3.fill = GridBagConstraints.HORIZONTAL;
        c3.gridx = 2;
        c3.gridy = 0;
        c3.weightx = 0.55;
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        final Map<UUID, List<PositionData>> positions = new HashMap<>();
        for (FinalStandings result : results) {
            for (PlayerStats stats : result.getStats()) {
                boolean dnf = stats.hitpoints < 1; // Check if the player did not finish
                positions.computeIfAbsent(stats.id, id -> new ArrayList<>()).add(new PositionData(stats.position, dnf));
            }
        }

        if (results.isEmpty()) {
            sortedParticipants = new ArrayList<>(participants);
        } else {
            sortedParticipants = participants.stream()
            .sorted((p1, p2) -> compare(p1.getId(), p2.getId(), positions))
            .collect(Collectors.toList());
        }
        // Header
        panel.add(new JLabel(), c1);
        panel.add(new JLabel(), c2);
        final JPanel trackPanel = new JPanel(new GridLayout(0, tracksAndLaps.size() + 1));
        trackPanel.add(new JLabel());
        for (int i = 0; i < tracksAndLaps.size(); ++i) {
            final JLabel trackInfo = new JLabel(Integer.toString(i + 1));
            trackInfo.setBorder(new EmptyBorder(0, 3, 0, 3));
            trackInfo.setHorizontalAlignment(SwingConstants.CENTER);
            trackInfo.setFont(new Font("Arial", Font.BOLD, 20));
            trackPanel.add(trackInfo);
            final int index = i;
            trackInfo.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    final ImageIcon icon = TrackPreviewButton.createIcon(tracksAndLaps.get(index).getKey());
                    if (results.size() > index) {
                        final PlayerStats[] stats = results.get(index).getStats();
                        Map<String, UUID> idMapper = new HashMap<>();
                        for (PlayerStats stat : stats) idMapper.put(stat.playerId, stat.id);
                        final PlayerRenderer renderer = (g2d, x, y, i1, playerId) -> participants.stream().filter(p -> p.getId().equals(idMapper.get(playerId))).findAny().ifPresent(p -> {
                            final String name = p.getName();
                            g2d.drawString(name, x + 30, y + (i1 + 1) * 15 + 15);
                            Player.draw(g2d, x + 15, y + (i1 + 1) * 15 + 10, 0, p.getColors(), 1.0);
                        });
                        final JDialog dialog = new JDialog(frame);
                        final int height = 5 + 15 * (stats.length + 1);
                        final JPanel panel = new JPanel() {
                            @Override
                            public void paintComponent(Graphics g) {
                                super.paintComponent(g);
                                g.setColor(Color.GRAY.brighter());
                                g.fillRect(0, 0, getWidth(), getHeight());
                                final double maxDistance = tracksAndLaps.get(index).getKey().getNodes().stream().mapToDouble(Node::getDistance).max().orElse(0);
                                Game.drawStandings((Graphics2D) g, 10, 10, stats, renderer, maxDistance);
                                if (icon != null) {
                                    icon.paintIcon(this, g, 30, height + 20);
                                }
                            }
                        };
                        panel.setPreferredSize(new Dimension(460, height + (icon == null ? 20 : 330)));
                        dialog.setTitle(tracksAndLaps.get(index).getKey().getName() + " results");
                        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                        dialog.setContentPane(panel);
                        dialog.pack();
                        dialog.setModal(true);
                        dialog.setLocationRelativeTo(frame);
                        dialog.setVisible(true);
                    } else if (icon != null) {
                        final JDialog dialog = new JDialog(frame);
                        final JPanel panel = new JPanel() {
                            @Override
                            public void paintComponent(Graphics g) {
                                super.paintComponent(g);
                                g.setColor(Color.GRAY.brighter());
                                g.fillRect(0, 0, getWidth(), getHeight());
                                icon.paintIcon(this, g, 10, 10);
                            }
                        };
                        panel.setPreferredSize(new Dimension(20 + icon.getIconWidth(), 20 + icon.getIconHeight()));
                        dialog.setTitle("Upcoming track: " + tracksAndLaps.get(index).getKey().getName());
                        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                        dialog.setContentPane(panel);
                        dialog.pack();
                        dialog.setModal(true);
                        dialog.setLocationRelativeTo(frame);
                        dialog.setVisible(true);
                    }
                }
                @Override
                public void mouseEntered(MouseEvent e) {
                    trackInfo.setForeground(Color.RED);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    trackInfo.setForeground(Color.BLACK);
                }
            });
        }
        panel.add(trackPanel, c3);
        for (int rank = 0; rank < sortedParticipants.size(); ++rank) {
            final ProfileMessage player = sortedParticipants.get(rank);
            final JLabel pos = new JLabel((rank + 1) + ".");
            final JLabel label = new JLabel(player.getName());
            final JPanel ptsTable = new JPanel(new GridLayout(0, tracksAndLaps.size() + 1));
            final int total = results.isEmpty() ? 0 : positions.get(player.getId()).stream().mapToInt(pd -> pd.dnf ? 0 : getPoints(pd.position)).sum();
            final JLabel pts = new JLabel(Integer.toString(total));
            pos.setFont(new Font("Arial", Font.BOLD, 20));
            label.setFont(new Font("Arial", Font.BOLD, 20));
            pts.setFont(new Font("Arial", Font.BOLD, 20));
            label.setIcon(new CarIcon(player.getColors()));
            label.setIconTextGap(20);
            pos.setHorizontalAlignment(SwingConstants.RIGHT);
            label.setBorder(new EmptyBorder(0, 0, 0, 10));
            pos.setBorder(new EmptyBorder(0, 0, 0, 10));
            pts.setBorder(new EmptyBorder(0, 0, 0, 10));
            c1.gridy = rank + 1;
            c2.gridy = rank + 1;
            c3.gridy = rank + 1;
            panel.add(pos, c1);
            panel.add(label, c2);
            panel.add(ptsTable, c3);
            ptsTable.add(pts);
            for (int i = 0; i < tracksAndLaps.size(); ++i) {
                final JLabel ptsLabel;
                if (results.size() > i) {
                    PositionData pd = positions.get(player.getId()).get(i);
                    ptsLabel = new JLabel(Integer.toString(pd.dnf ? 0 : getPoints(pd.position)));
                } else {
                    ptsLabel = new JLabel("-");
                }
                ptsLabel.setHorizontalAlignment(SwingConstants.CENTER);
                ptsTable.add(ptsLabel);
            }
        }
        return panel;
    }

    void showStandings(WindowChanger listener) {
        masterPanel = new JPanel();
        masterPanel.setLayout(new BoxLayout(masterPanel, BoxLayout.Y_AXIS));
        masterPanel.add(createStandingsPanel());
        continueButton = new JButton("Next Race");
        continueButton.addActionListener(a -> {
            final TrackData data = tracksAndLaps.get(results.size()).getLeft();
            if (data.getBackgroundImage() == null) {
                JOptionPane.showConfirmDialog(frame, "Missing track image: " + data.getTrackId(), "Error", JOptionPane.DEFAULT_OPTION);
                return;
            }
            final int laps = tracksAndLaps.get(results.size()).getRight();
            final List<PlayerSlot> slots = new ArrayList<>(sortedParticipants.size());
            for (int i = 0; i < sortedParticipants.size(); ++i) {
                final int pos = i + 1;
                slots.add(new PlayerSlot(sortedParticipants.get(sortedParticipants.size() - pos), pos));
            }
            final Main server = new Main(new Main.Params(laps, animationDelayMs, timePerTurnMs, leewayMs, maxHitpoints, tireChanges), null, frame, masterPanel, slots, data, Season.this);
            listener.contentChanged(server, null, server, "championship race", true);
            Main.setContent(frame, server);
            new Thread(server).start();
        });
        final boolean complete = results.size() == tracksAndLaps.size();
        masterPanel.add(continueButton);
        continueButton.setEnabled(!complete);
        listener.contentChanged(masterPanel, null, null, "Season " + name, false);
        frame.setContentPane(masterPanel);
        frame.pack();
    }

    boolean load() {
        try (InputStreamReader ir = new InputStreamReader(new FileInputStream(name + ".cha"), StandardCharsets.UTF_8); final BufferedReader br = new BufferedReader(ir)) {
            int phase = 0;
            String line;
            final List<PlayerStats> stats = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    ++phase;
                    if (phase >= 3) {
                        if (!stats.isEmpty()) {
                            results.add(new FinalStandings(stats, true));
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
                            if (parts.length > 5) {
                                maxHitpoints = Integer.parseInt(parts[5]);
                                maxHitpoints = Math.max(SettingsField.minHP, maxHitpoints);
                                maxHitpoints = Math.min(SettingsField.maxHP, maxHitpoints);
                            } else {
                                maxHitpoints = 18;
                            }
                            if (parts.length > 6) {
                                pointDistribution = PointDistributionField.stringToDist(parts[6]);
                            }
                            if (parts.length > 7) {
                                tireChanges = Boolean.parseBoolean(parts[7]);
                            }
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

    void updateProfileInfo(List<Profile> profiles) {
        participants.forEach(pm -> {
            // Try to find original profile for non-AI players
            // If profile has been deleted, replace the plyaer with AI player
            if (!pm.isAi()) {
                final Profile og = profiles.stream().filter(p -> p.getId().equals(pm.getId())).findAny().orElse(null);
                if (og == null) {
                    pm.setAi(true);
                } else {
                    pm.originalProfile = og;
                }
            }
        });
    }

    void updateResult(FinalStandings fs) {
        results.add(fs);
        masterPanel.removeAll();
        masterPanel.add(createStandingsPanel());
        masterPanel.add(continueButton);
        final boolean complete = results.size() == tracksAndLaps.size();
        continueButton.setEnabled(!complete);
        save();
    }

    private void save() {
        timeStamp = System.currentTimeMillis();
        try (final PrintWriter writer = new PrintWriter(name + ".cha", "UTF-8")) {
            writer.print(name);
            writer.print(",");
            writer.print(timeStamp);
            writer.print(",");
            writer.print(animationDelayMs);
            writer.print(",");
            writer.print(timePerTurnMs);
            writer.print(",");
            writer.print(leewayMs);
            writer.print(",");
            writer.print(maxHitpoints);
            writer.print(",");
            writer.print(PointDistributionField.distToString(pointDistribution));
            writer.print(",");
            writer.println(Boolean.toString(tireChanges));
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
                writer.println(message.toLine());
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
    public int compareTo(@Nonnull Season season) {
        return (int) ((season.timeStamp - timeStamp) / 1000);
    }

    private static class TrackButton extends JButton {
        private final TrackData data;
        private final SettingsField laps;
        private TrackButton(TrackData data, SettingsField laps) {
            final String name = data.getName();
            setText(name);
            this.data = data;
            this.laps = laps;
        }
    }

    @Override
    public void setTrack(TrackData data, ImageIcon icon) {
        final SettingsField lapsField = new SettingsField(buttonPanel, "Laps", Integer.toString(Main.settings.laps), 1, 200);
        final TrackButton button = new TrackButton(data, lapsField);
        final int count = buttonPanel.getComponentCount();
        buttonPanel.add(button, count - 1);
        buttonPanel.add(lapsField, count);
        frame.pack();
        button.addActionListener(a -> {
            if (tracks.size() < 4) return;
            buttonPanel.remove(button);
            buttonPanel.remove(lapsField);
            frame.pack();
            tracks.remove(button);
            final int playerCount = getPlayerCount();
            // Add new slots if needed
            boolean modified = false;
            while (slots.size() < playerCount) {
                final PlayerSlot slot = new PlayerSlot(frame, localProfiles, null, slots, slots.size() + 1) {
                    @Override
                    public String getText() {
                        return profile == null ? "Free" : profile.getName();
                    }
                };
                playerPanel.add(slot);
                slots.add(slot);
                modified = true;
            }
            if (modified) {
                playerPanel.repaint();
                frame.pack();
            }
        });
        tracks.add(button);
        final int playerCount = getPlayerCount();
        // Remove unused slots if possible
        boolean modified = false;
        while (slots.size() > playerCount) {
            final PlayerSlot slot = slots.get(slots.size() - 1);
            if (slot.isFree()) {
                slots.remove(slots.size() - 1);
                playerPanel.remove(slot);
                modified = true;
            } else {
                break;
            }
        }
        if (modified) {
            playerPanel.repaint();
            frame.pack();
        }
    }
}
