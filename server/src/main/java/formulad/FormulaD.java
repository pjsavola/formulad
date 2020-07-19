package formulad;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import formulad.ai.*;

import formulad.ai.Node;
import formulad.model.*;
import formulad.model.Gear;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;

public class FormulaD extends Game implements Runnable {
    private final Map<Node, List<Node>> prevNodeMap;
    private LocalPlayer current;
    private LocalPlayer previous;
    private List<LocalPlayer> waitingPlayers = new ArrayList<>();
    private final List<LocalPlayer> allPlayers;
    private final List<LocalPlayer> players = new ArrayList<>();
    private final List<LocalPlayer> stoppedPlayers = new ArrayList<>();
    private final Map<LocalPlayer, AI> aiMap = new HashMap<>();
    private final Random rng;
    private final Map<Node, Double> distanceMap = new HashMap<>();
    private boolean stopped;
    private boolean enableTimeout;
    private final int initTimeoutInMillis;
    private final int gearTimeoutInMillis;
    private final int moveTimeoutInMillis;
    private final Set<LocalPlayer> disconnectedPlayers = new HashSet<>();
    private final Lobby lobby;
    public static final Logger log = Logger.getLogger(FormulaD.class.getName());
    static {
        try {
            log.setUseParentHandlers(false);
            final Handler handler = new FileHandler("formulad.log");
            handler.setFormatter(new CustomRecordFormatter());
            log.addHandler(handler);
        } catch (IOException e) {
            System.err.println("FileHandler for file formulad.log could not be adeded");
            e.printStackTrace();
        }
    }

    public FormulaD(Params params, Lobby lobby, JFrame frame, JPanel panel, PlayerSlot[] slots, String trackId) {
        super(frame, panel);
        initTrack(trackId);
        this.lobby = lobby;
        prevNodeMap = AIUtil.buildPrevNodeMap(nodes);
        LocalPlayer.animationDelayInMillis = params.animationDelayInMillis;
        final long seed = params.seed == null ? new Random().nextLong() : params.seed;
        this.rng = new Random(seed);
        log.info("Initializing RNG with seed " + seed);
        initTimeoutInMillis = params.initTimeoutInMillis;
        gearTimeoutInMillis = params.gearTimeoutInMillis;
        moveTimeoutInMillis = params.moveTimeoutInMillis;
        allPlayers = new ArrayList<>();
        createGrid(params, attributes, slots, frame);
        waitingPlayers.addAll(players);
        waitingPlayers.sort((p1, p2) -> p1.compareTo(p2, distanceMap, stoppedPlayers));
        final List<PlayerStats> stats = new ArrayList<>();
        for (int i = 0; i < waitingPlayers.size(); i++) {
            final LocalPlayer player = waitingPlayers.get(i);
            final PlayerStats playerStats = player.getStatistics(i + 1, distanceMap);
            stats.add(playerStats);
        }
        allPlayers.sort((p1, p2) -> p1.compareTo(p2, distanceMap, stoppedPlayers));
        standings = new ArrayList<>(allPlayers);
        notifyAll(new FinalStandings(stats));
        current = waitingPlayers.remove(0);
    }

    private void createGrid(Params params, Map<Node, Double> attributes, PlayerSlot[] slots, JFrame frame) {
        final Map<AI, ProfileMessage> aiToProfile = new LinkedHashMap<>(); // preserve order
        for (PlayerSlot slot : slots) {
            final ProfileMessage profile = slot.getProfile();
            if (profile != null) {
                if (profile.isAi()) {
                    aiToProfile.put(new GreatAI(), profile);
                }
                else if (profile.isLocal()) {
                    aiToProfile.put(new ManualAI(new GreatAI(), frame, this, profile.originalProfile), profile);
                }
                else if (lobby != null) {
                    final RemoteAI client = lobby.getClient(profile.getId());
                    if (client != null) {
                        aiToProfile.put(client, profile);
                    }
                }
            }
        }
        final int playerCount = aiToProfile.size();
        final List<Node> grid = findGrid(nodes, attributes, distanceMap, prevNodeMap).subList(0, playerCount);
        grid.sort((n1, n2) -> (int) (100 * (distanceMap.get(n2) - distanceMap.get(n1))));

        final List<Integer> startingOrder = IntStream.range(0, playerCount).boxed().collect(Collectors.toList());
        if (params.randomizeStartingOrder) {
            Collections.shuffle(startingOrder, rng);
        }
        enableTimeout = true;
        final List<CreatedPlayerNotification> notifications = new ArrayList<>();
        for (Map.Entry<AI, ProfileMessage> e : aiToProfile.entrySet()) {
            notifications.add(createAiPlayer(e, grid, startingOrder, attributes, params.leeway, params.laps));
        }
        notifications.forEach(this::notifyAll);
        immutablePlayerMap = new HashMap<>(aiToProfile.size());
        allPlayers.forEach(player -> immutablePlayerMap.put(player.getId(), player));
    }

    private CreatedPlayerNotification createAiPlayer(Map.Entry<AI, ProfileMessage> ai, List<Node> grid, List<Integer> startingOrder, Map<Node, Double> attributes, int leeway, int laps) {
        // Recreate track for each player, so nothing bad happens if AI mutates it.
        final int playerCount = allPlayers.size();
        final String playerId = "p" + (playerCount + 1);
        final Track track = ApiHelper.buildTrack(trackId, playerId, nodes, attributes);
        final Node startNode = grid.get(startingOrder.get(playerCount));
        final LocalPlayer player = new LocalPlayer(playerId, startNode, attributes.get(startNode), laps, this, leeway, ai.getValue().getColor1(), ai.getValue().getColor2());
        current = player;
        log.info("Initializing player " + playerId);
        NameAtStart nameResponse = getAiInput(() -> ai.getKey().startGame(track), initTimeoutInMillis);
        /*
        final String name;
        final UUID id;
        if (nameResponse == null) {
            log.warning("Received no name from player " + playerId + ", using name Unresponsive instead");
            name = "Unresponsive";
            id = UUID.randomUUID();
        } else {
            name = nameResponse.getName();
            id = nameResponse.getId();
        }*/
        final String name = ai.getValue().getName();
        final UUID id = ai.getValue().getId();
        log.info("Initialization done, player " + name + " starts from position " + (startingOrder.get(playerCount) + 1));
        player.setName(name);
        player.setId(id);
        players.add(player);
        allPlayers.add(player);
        player.setGridPosition(allPlayers.size());
        aiMap.put(player, ai.getKey());
        return new CreatedPlayerNotification(current.getId(), name, startNode.getId(), 18, 1, ai.getValue().getColor1(), ai.getValue().getColor2());
    }

    public void notifyAll(Object notification) {
        aiMap.values().forEach(a -> a.notify(notification));
    }

    private <T> T getAiInput(Supplier<T> supplier, int timeout) {
        T result = null;
        boolean exception = false;
        final long startTime = System.currentTimeMillis();
        final CompletableFuture<T> future = CompletableFuture.supplyAsync(supplier);
        try {
            if (enableTimeout) {
                result = future.get(timeout + current.getLeeway(), TimeUnit.MILLISECONDS);
            } else {
                result = future.get();
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            exception = true;
            log.log(Level.WARNING, "AI request timed out", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            exception = true;
            log.log(Level.WARNING, "AI request interrupted", e);
        } catch (ExecutionException e) {
            exception = true;
            log.log(Level.WARNING, "AI request raised an exception", e);
        }
        final long timeSpent = System.currentTimeMillis() - startTime;
        current.recordTimeUsed(timeSpent, exception);
        if (enableTimeout && timeSpent > timeout) {
            current.reduceLeeway(timeSpent - timeout);
        }
        final AI ai = aiMap.get(current);
        if (ai instanceof RemoteAI) {
            final RemoteAI client = (RemoteAI) ai;
            if (!client.isConnected() && disconnectedPlayers.add(current)) {
                current.setName(current.getName() + " (DC)");
            }
        }
        return result;
    }

    @Override
    public void run() {
        final boolean hasManualAI = aiMap.values().stream().anyMatch(ai -> ai instanceof ManualAI);
        while (!stopped) {
            current.beginTurn();
            final AI ai = aiMap.get(current);
            final GameState gameState = ApiHelper.buildGameState(trackId, players);
            log.info("Querying gear input from AI " + current.getNameAndId());
            final Gear gearResponse = getAiInput(() -> ai.selectGear(gameState), gearTimeoutInMillis);
            if (!hasManualAI || ai instanceof ManualAI) {
                updateHitpointMap(gameState);
            }
            final Integer selectedGear = gearResponse == null ? null : gearResponse.getGear();
            if (selectedGear != null && current.switchGear(selectedGear)) {
                log.info("Gear input received: " + selectedGear);
            } else {
                log.warning("Invalid gear selection " + selectedGear + ", using current gear instead");
            }
            if (previous != null) {
                previous.clearRoute();
            }
            roll = current.roll(rng);
            final Moves allMoves = current.findAllTargets(roll, trackId, players);
            if (current.getLeeway() <= 0) {
                log.info("Player " + current.getNameAndId() + " used his timeout leeway and was dropped from the game");
                current.stop();
            } else if (allMoves.getMoves().isEmpty()) {
                log.info("No valid targets after dice roll " + roll + ", DNF");
                current.stop();
            } else {
                log.info("Querying move input from AI " + current.getNameAndId());
                final SelectedIndex moveResponse = getAiInput(() -> ai.selectMove(allMoves), moveTimeoutInMillis);
                Integer selectedIndex = moveResponse == null ? null : moveResponse.getIndex();
                if (selectedIndex == null || selectedIndex < 0 || selectedIndex >= allMoves.getMoves().size()) {
                    log.warning("Invalid move selection " + selectedIndex + ", using index 0 instead");
                    selectedIndex = 0;
                } else {
                    log.info("Move input received: " + selectedIndex);
                }
                current.move(selectedIndex, coordinates, attributes);
                current.collide(players, prevNodeMap, rng);
                if (roll == 20 || roll == 30) {
                    LocalPlayer.possiblyAddEngineDamage(players, rng);
                }
            }
            roll = null;
            nextPlayer();
            repaint();
        }
        final List<PlayerStats> stats = new ArrayList<>();
        for (int i = 0; i < stoppedPlayers.size(); i++) {
            final LocalPlayer player = stoppedPlayers.get(i);
            final PlayerStats playerStats = player.getStatistics(i + 1, distanceMap);
            stats.add(playerStats);
        }
        final FinalStandings fs = new FinalStandings(stats);
        finalStandings = fs.getStats();
        notifyAll(fs);
        if (lobby != null) {
            lobby.close();
        }
        repaint();
        clickToExit();
    }

    static List<Node> findGrid(List<Node> nodes, Map<Node, Double> attributes, Map<Node, Double> distanceMap, Map<Node, List<Node>> prevNodeMap) {
	    final Set<Node> visited = new HashSet<>();
        final List<Node> grid = new ArrayList<>();
        final List<Node> work = new ArrayList<>();
        final List<Node> edges = new ArrayList<>();
        Node center = null;
        for (Node node : nodes) {
            if (node.getType() == Node.Type.FINISH) {
                work.add(node);
                visited.add(node);
                if (node.childCount(Node.Type.PIT) == 3) {
                    center = node;
                } else {
                    edges.add(node);
                }
            }
        }
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            if (node.getType() == Node.Type.START) {
                grid.add(0, node);
            }
            node.forEachChild(next -> {
                if (visited.add(next)) {
                    work.add(next);
                }
            });
        }
        if (center == null) {
            throw new RuntimeException("Finish line must have width 3");
        }
        if (center.hasChildren(edges)) {
            distanceMap.put(center, 0.0);
        } else {
            distanceMap.put(center, 0.5);
            distanceMap.put(edges.get(0), 0.0);
            distanceMap.put(edges.get(1), 0.0);
        }
        work.add(center);
        final MutableObject<Node> pit = new MutableObject<>(null);
        final List<Node> curves = new ArrayList<>();
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            final long childCount = node.childCount(Node.Type.PIT);
            node.forEachChild(next -> {
                if (distanceMap.containsKey(next)) {
                    return;
                }
                if (next.isCurve()) {
                    curves.add(next);
                    return;
                }
                if (next.getType() == Node.Type.PIT) {
                    pit.setValue(next);
                    final Double pitEntryDistance = distanceMap.get(next);
                    final double pitStartDistance = distanceMap.get(node) - 0.4;
                    if (pitEntryDistance == null || pitStartDistance < pitEntryDistance) {
                        distanceMap.put(next, pitStartDistance);
                    }
                    return;
                }
                final long nextChildCount = next.childCount(Node.Type.PIT);
                final boolean fromCenterToEdge = childCount == 3 && nextChildCount == 2;
                distanceMap.put(next, distanceMap.get(node) + (fromCenterToEdge ? 0.5 : 1));
                work.add(next);
            });
            if (work.isEmpty() && !curves.isEmpty()) {
                final double maxDistance = distanceMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
                for (Node curve : curves) {
                    final double relativeDistance = attributes.get(curve);
                    distanceMap.put(curve, maxDistance + relativeDistance);
                }
                while (!curves.isEmpty()) {
                    final Node curve = curves.remove(0);
                    curve.forEachChild(next -> {
                        if (distanceMap.containsKey(next)) {
                            return;
                        }
                        if (!next.isCurve()) {
                            work.add(next);
                            return;
                        }
                        curves.add(next);
                        distanceMap.put(next, attributes.get(next) + maxDistance);
                    });
                }
                final double newMaxDistance = distanceMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
                center = null;
                if (work.isEmpty()) {
                    throw new RuntimeException("Curve exit must have size > 0");
                }
                for (Node straight : work) {
                    boolean allCurves = true;
                    for (Node prev : prevNodeMap.get(straight)) {
                        if (!prev.isCurve()) {
                            allCurves = false;
                            break;
                        }
                    }
                    if (allCurves) {
                        distanceMap.put(straight, newMaxDistance);
                        for (Node otherStraight : work) {
                            if (straight.hasChild(otherStraight)) {
                                center = otherStraight;
                                break;
                            }
                        }
                        break;
                    }
                }
                work.clear();
                work.add(center);
                distanceMap.put(center, newMaxDistance + 0.5);
            }
        }
        while (pit.getValue() != null) {
            final Node node = pit.getValue();
            final long childCount = node.childCount(null);
            if (childCount > 1) {
                node.forEachChild(next -> {
                    if (next.getType() == Node.Type.PIT || !distanceMap.containsKey(next)) {
                        throw new RuntimeException("Pit lane is branching");
                    }
                });
                pit.setValue(null);
            } else if (childCount < 1) {
                throw new RuntimeException("Pit lane is dead end");
            } else {
                node.forEachChild(next -> {
                    distanceMap.put(next, distanceMap.get(node) + 0.01);
                    pit.setValue(next);
                });
            }
        }
        return grid;
    }

    public static boolean validateTrack(String trackId) {
        final List<Node> nodes = new ArrayList<>();
        final Map<Node, Double> attributes = new HashMap<>();
        final Map<Node, Double> distanceMap = new HashMap<>();
        final Map<Node, Point> coordinates = new HashMap<>();
        try (InputStream is = FormulaD.class.getResourceAsStream("/" + trackId)) {
            final Pair<String, MapEditor.Corner> result = MapEditor.loadNodes(is, nodes, attributes, coordinates);
            if (result == null) {
                log.log(Level.SEVERE, "Track validation failed: Proper header is missing from " + trackId);
                return false;
            }
            final Map<Node, List<Node>> prevNodeMap = AIUtil.buildPrevNodeMap(nodes);
            final List<Node> grid = findGrid(nodes, attributes, distanceMap, prevNodeMap);
            if (grid.size() < 10) {
                log.log(Level.SEVERE, "Track validation failed: Starting grid has less than 10 spots");
                return false;
            }
            final String imageFile = "/" + result.getLeft();
            final BufferedImage image = ImageCache.getImage(imageFile);
            if (image == null) {
                log.log(Level.SEVERE, "Track validation failed: Background image " + result.getLeft() + " not found");
                return false;
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Track validation failed: " + e.getMessage(), e);
            return false;
        }
        return true;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        //drawDistances((Graphics2D) g); // For debugging
    }

    private void drawDistances(Graphics2D g2d) {
        for (Map.Entry<Node, Double> entry : distanceMap.entrySet()) {
            final Point p = coordinates.get(entry.getKey());
            final int posX = p.x - 5;
            final int posY = p.y + 3;
            g2d.setFont(new Font("Arial", Font.PLAIN, 8));
            g2d.setColor(Color.BLUE);
            g2d.drawString(entry.getValue().toString(), posX, posY);
        }
    }

    private void nextPlayer() {
	    // Drop stopped players
	    final Iterator<LocalPlayer> it = players.iterator();
	    while (it.hasNext()) {
	        final LocalPlayer player = it.next();
	        if (player.isStopped()) {
	            stoppedPlayers.add(player);
	            waitingPlayers.remove(player);
	            it.remove();
            }
        }
        if (waitingPlayers.isEmpty()) {
            if (players.isEmpty()) {
                // Making a clone, because sorting depends on the order of stoppedPlayers list
                final List<LocalPlayer> clone = new ArrayList<>(stoppedPlayers);
                stoppedPlayers.sort((p1, p2) -> p1.compareTo(p2, distanceMap, clone));
                // This will make the game thread to stop.
                stopped = true;
                return;
            }
            // Set turn order for next round
            waitingPlayers.addAll(players);
            waitingPlayers.sort((p1, p2) -> p1.compareTo(p2, distanceMap, stoppedPlayers));
            // Sort info box contents to match with current standings and turn order
            allPlayers.sort((p1, p2) -> p1.compareTo(p2, distanceMap, stoppedPlayers));
            standings = new ArrayList<>(allPlayers);
            notifyAll(new Standings(allPlayers));
        }
        previous = current;
        current = waitingPlayers.remove(0);
    }

    private static class Params {
       private int animationDelayInMillis = 100;
        private int initTimeoutInMillis = 3000;
        private int gearTimeoutInMillis = 3000;
        private int moveTimeoutInMillis = 3000;
        private int leeway = 3600000;
        private Long seed = null;
        private boolean randomizeStartingOrder = false;
        private int laps = 1;
    }

    private static void showGameSettings(JFrame frame, JPanel panel, Lobby lobby, List<Profile> profiles, Params params, String trackId, WindowChanger listener) {
        final List<ProfileMessage> localProfiles = profiles.stream().map(ProfileMessage::new).collect(Collectors.toList());
        final JPanel playerPanel = new JPanel(new GridLayout(5, 2));
        final PlayerSlot[] slots = new PlayerSlot[10];
        for (int i = 0; i < slots.length; ++i) {
            final PlayerSlot slot = new PlayerSlot(frame, localProfiles, lobby, slots, i + 1);
            playerPanel.add(slot);
            slots[i] = slot;
        }
        if (lobby != null) {
            lobby.setSlots(slots);
        }
        final TrackPreviewButton changeTrackButton = new TrackPreviewButton(frame, panel, lobby);
        if (!changeTrackButton.setTrack(trackId)) {
            return;
        }
        final JPanel lobbyPanel = new JPanel();
        lobbyPanel.setName(lobby == null ? "Game Settings" : "Multiplayer Game Settings");
        lobbyPanel.setLayout(new BoxLayout(lobbyPanel, BoxLayout.PAGE_AXIS));
        lobbyPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        final JButton startButton = new JButton("Start");
        final JCheckBox randomStartingOrder = new JCheckBox("Randomize starting order", true);
        final SettingsField laps = new SettingsField(lobbyPanel, "Laps", "1", 1, 200);
        final SettingsField animationDelay = new SettingsField(lobbyPanel, "Animation delay (ms)", "100", 0, 1000);
        final SettingsField time = new SettingsField(lobbyPanel, "Time per turn (s)", "3", 0, 3600);
        final SettingsField leeway = new SettingsField(lobbyPanel, "Time leeway (s)", "3600", 0, 36000);
        startButton.addActionListener(event -> {
            boolean hasPlayers = false;
            final Set<UUID> ids = new HashSet<>();
            for (PlayerSlot slot : slots) {
                final ProfileMessage profile = slot.getProfile();
                if (profile != null) {
                    hasPlayers = true;
                    if (profile == ProfileMessage.pending) {
                        JOptionPane.showConfirmDialog(lobbyPanel, "Someone is about to join", "Error", JOptionPane.DEFAULT_OPTION);
                        return;
                    }
                    else if (!profile.isAi() && !ids.add(profile.getId())) {
                        JOptionPane.showConfirmDialog(lobbyPanel, "Duplicate profile: " + profile.getName(), "Error", JOptionPane.DEFAULT_OPTION);
                        return;
                    }
                }
            }
            if (!hasPlayers) {
                JOptionPane.showConfirmDialog(lobbyPanel, "Need at least 1 player", "Error", JOptionPane.DEFAULT_OPTION);
                return;
            }
            try {
                params.laps = laps.getValue();
                params.animationDelayInMillis = animationDelay.getValue();
                params.initTimeoutInMillis = time.getValue() * 1000;
                params.gearTimeoutInMillis = time.getValue() * 1000;
                params.moveTimeoutInMillis = time.getValue() * 1000;
                params.leeway = leeway.getValue() * 1000;
            } catch (NumberFormatException ex) {
                return;
            }
            if (lobby != null) {
                lobby.done = true;
                lobby.interrupt();
            }
            params.randomizeStartingOrder = randomStartingOrder.isSelected();
            final FormulaD server = new FormulaD(params, lobby, frame, panel, slots, changeTrackButton.getTrack());
            listener.contentChanged(server, lobby, server, lobby == null ? "race" : "server", true);
            setContent(frame, server);
            new Thread(server).start();
        });
        final JPanel gridPanel = new JPanel(new GridLayout(2, 2));
        gridPanel.add(changeTrackButton);
        gridPanel.add(playerPanel);
        final JPanel settings = new JPanel();
        settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
        randomStartingOrder.setAlignmentX(Component.LEFT_ALIGNMENT);
        laps.setAlignmentX(Component.LEFT_ALIGNMENT);
        animationDelay.setAlignmentX(Component.LEFT_ALIGNMENT);
        time.setAlignmentX(Component.LEFT_ALIGNMENT);
        leeway.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(randomStartingOrder);
        settings.add(laps);
        settings.add(animationDelay);
        settings.add(time);
        settings.add(leeway);
        gridPanel.add(settings);
        gridPanel.add(startButton);
        lobbyPanel.add(gridPanel);
        listener.contentChanged(lobbyPanel, lobby, null, "server", lobby != null);
        frame.setContentPane(lobbyPanel);
        frame.pack();
        if (lobby != null) {
            lobby.start();
        }
    }

    private static void setContent(JFrame f, JPanel p) {
        p.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Rectangle r = new Rectangle(e.getX(), e.getY(), 1, 1);
                ((JPanel) e.getSource()).scrollRectToVisible(r);
            }
        });
        p.setAutoscrolls(true);
        final JScrollPane scrollPane = new JScrollPane(p);
        scrollPane.setName(p.getName());
        f.setContentPane(scrollPane);
        f.pack();
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        f.setSize(Math.min(screenSize.width, f.getWidth()), Math.min(screenSize.height, f.getHeight()));
    }

    static class ProfileSaver extends WindowAdapter {
        private final JFrame frame;
        private final Profile.Manager profileManager;

        ProfileSaver(JFrame frame, List<Profile> profiles) {
            this.frame = frame;
            this.profileManager = profiles.isEmpty() ? null : profiles.get(0).getManager();
        }

        @Override
        public void windowClosing(WindowEvent e) {
            if (profileManager != null) {
                profileManager.saveProfiles();
            }
        }
    }

    private static void showMenu(JFrame f, Params params, List<Profile> profiles) {
        final JPanel p = new JPanel();
        final WindowChanger listener = new WindowChanger(f, p);
        f.addWindowListener(listener);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        final JPanel title = new JPanel();
        final JLabel titleText = new JLabel("FORMULA D ONLINE");
        titleText.setFont(new Font("Arial", Font.BOLD, 32));
        title.add(titleText);
        final JPanel contents = new JPanel(new GridLayout(0, 2));
        p.add(title);
        p.setName("Main Menu");
        p.add(contents);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        final JPanel buttonPanel = new JPanel(new GridLayout(4, 0));
        final ProfilePanel profilePanel = new ProfilePanel(profiles);
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contents.add(buttonPanel);
        contents.add(profilePanel);

        final JButton singlePlayerButton = new JButton("Single Player");
        singlePlayerButton.addActionListener(e -> {
            showGameSettings(f, p, null, profiles, params, profilePanel.getActiveProfile().getLastTrack(), listener);
        });
        final JButton hostMultiplayerButton = new JButton("Host Multiplayer");
        hostMultiplayerButton.addActionListener(e -> {
            String result = (String) JOptionPane.showInputDialog(f, "Select port", "Select port", JOptionPane.PLAIN_MESSAGE,  null, null, "1277");
            if (result == null) {
                return;
            }
            try {
                final int port = Integer.parseInt(result);
                final Lobby lobby = new Lobby(port);
                final String trackId = profilePanel.getActiveProfile().getLastTrack();
                showGameSettings(f, p, lobby, profiles, params, trackId, listener);
            } catch (NumberFormatException exception) {
                JOptionPane.showConfirmDialog(p, "Invalid port: '" + result + "'", "Error", JOptionPane.DEFAULT_OPTION);
            } catch (IOException exception) {
                JOptionPane.showConfirmDialog(p, "Unable to start server with port " + result, "Error", JOptionPane.DEFAULT_OPTION);
            } catch (IllegalArgumentException exception) {
                JOptionPane.showConfirmDialog(p, "Illegal port: " + result, "Error", JOptionPane.DEFAULT_OPTION);
            }
        });
        final JButton joinMultiplayerButton = new JButton("Join Multiplayer");
        joinMultiplayerButton.addActionListener(e -> {
            String result = (String) JOptionPane.showInputDialog(p, "IP address and port", "IP address and port", JOptionPane.PLAIN_MESSAGE,  null, null, "localhost:1277");
            if (result == null) {
                return;
            }
            String[] addressAndPort = result.split(":");
            try {
                if (addressAndPort.length == 2) {
                    final int port = Integer.parseInt(addressAndPort[1]);
                    Socket socket = new Socket(addressAndPort[0], port);
                    final Client client = new Client(f, socket, p, profilePanel.getActiveProfile());
                    listener.contentChanged(client, null, client, "client", true);
                    setContent(f, client);
                    new Thread(client).start();
                } else {
                    JOptionPane.showConfirmDialog(p, "Please specify server IP address and port (for example 123.456.7.8:1277)", "Error", JOptionPane.DEFAULT_OPTION);
                }
            } catch (NumberFormatException exception) {
                JOptionPane.showConfirmDialog(p, "Invalid port: '" + addressAndPort[1] + "'", "Error", JOptionPane.DEFAULT_OPTION);
            } catch (IOException exception) {
                JOptionPane.showConfirmDialog(p, "Unable to connect to server " + result, "Error", JOptionPane.DEFAULT_OPTION);
            }
        });
        final JButton trackEditorButton = new JButton("Track Editor");
        trackEditorButton.addActionListener(e -> {
            final MapEditor editor = new MapEditor(f);
            if (editor.open()) {
                listener.contentChanged(editor, null, null, "track editor", true);
                setContent(f, editor);
            }
        });
        singlePlayerButton.setFont(new Font("Arial", Font.BOLD, 20));
        hostMultiplayerButton.setFont(new Font("Arial", Font.BOLD, 20));
        joinMultiplayerButton.setFont(new Font("Arial", Font.BOLD, 20));
        trackEditorButton.setFont(new Font("Arial", Font.BOLD, 20));
        singlePlayerButton.setPreferredSize(new Dimension(80, 40));
        hostMultiplayerButton.setPreferredSize(new Dimension(80, 40));
        joinMultiplayerButton.setPreferredSize(new Dimension(80, 40));
        trackEditorButton.setPreferredSize(new Dimension(80, 40));
        buttonPanel.add(singlePlayerButton);
        buttonPanel.add(hostMultiplayerButton);
        buttonPanel.add(joinMultiplayerButton);
        buttonPanel.add(trackEditorButton);
        f.setContentPane(p);
        f.pack();
        f.addWindowListener(new ProfileSaver(f, profiles));
        f.setVisible(true);
    }

    public static void main(String[] args) {
        final JFrame f = new JFrame();
        final Params params = new Params();
        final Profile.Manager profileManager = new Profile.Manager();
        final List<Profile> profiles = new ArrayList<>();
        try {
            final FileInputStream fileIn = new FileInputStream("profiles.dat");
            final ObjectInputStream objectIn = new ObjectInputStream(fileIn);
            while (true) {
                final Object obj = objectIn.readObject();
                if (obj == null) {
                    break;
                } else if (obj instanceof Profile) {
                    final Profile profile = (Profile) obj;
                    profiles.add(profile);
                    profile.setManager(profileManager);
                }
            }
            objectIn.close();
        } catch (IOException | ClassNotFoundException e) {
            if (profiles.isEmpty()) {
                final Profile defaultProfile = new Profile(profileManager, "Player");
                defaultProfile.setActive(true);
                profiles.add(defaultProfile);
            }
        }

        boolean activeFound = false;
        for (Profile profile : profiles) {
            if (profile.isActive()) {
                if (activeFound) {
                    profile.setActive(false);
                }
                activeFound = true;
            }
        }
        if (!activeFound) {
            profiles.get(0).setActive(true);
        }
        showMenu(f, params, profiles);
    }

    @Override
    protected LocalPlayer getCurrent() {
        return current;
    }
}
