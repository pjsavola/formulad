package gp;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import gp.ai.*;

import gp.ai.Node;
import gp.model.*;
import gp.model.Gear;
import org.apache.commons.lang3.tuple.Pair;

class PreviousSettings {
    String trackId = "hockenheim.dat";
    boolean external;
    String port = "1277";
    String server = "localhost:1277";
    boolean randomStartOrder = true;
    int laps = 1;
    int animationDelay = 60;
    int timePerTurn = 3;
    int leeway = 3600;
    int maxHitpoints = 18;
}

public class Main extends Game implements Runnable {
    private Map<Node, Set<Node>> collisionMap;
    private LocalPlayer current;
    private LocalPlayer previous;
    private List<LocalPlayer> waitingPlayers = new ArrayList<>();
    private final List<LocalPlayer> allPlayers;
    private final List<LocalPlayer> players = new ArrayList<>();
    private final List<LocalPlayer> stoppedPlayers = new ArrayList<>();
    private final Map<LocalPlayer, AI> aiMap = new HashMap<>();
    private final Random rng;
    private boolean stopped;
    private boolean interrupted;
    private boolean enableTimeout;
    private final int gearTimeoutInMillis;
    private final int moveTimeoutInMillis;
    private final Set<LocalPlayer> disconnectedPlayers = new HashSet<>();
    private final Lobby lobby;
    private final Season resultStorage;
    public static final Logger log = Logger.getLogger(Main.class.getName());
    public static int defaultColor1 = 0xFF9966;
    public static int defaultColor2 = 0xCCCC33;
    public static boolean ide = false;
    public static boolean sounds = true;

    static PreviousSettings settings = new PreviousSettings();

    static {
        try {
            log.setUseParentHandlers(false);
            final Handler handler = new FileHandler("gp.log");
            handler.setFormatter(new CustomRecordFormatter());
            log.addHandler(handler);
        } catch (IOException e) {
            System.err.println("FileHandler for file gp.log could not be adeded");
            e.printStackTrace();
        }
    }

    public Main(Params params, Lobby lobby, JFrame frame, JPanel panel, PlayerSlot[] slots, TrackData trackData, Season resultStorage) {
        super(frame, panel);
        initTrack(trackData);
        settings.trackId = trackData.getTrackId();
        settings.external = trackData.isExternal();
        this.lobby = lobby;
        this.resultStorage = resultStorage;
        LocalPlayer.animationDelayInMillis = params.animationDelayInMillis;
        final long seed = params.seed == null ? new Random().nextLong() : params.seed;
        this.rng = new Random(seed);
        log.info("Initializing RNG with seed " + seed);
        gearTimeoutInMillis = params.gearTimeoutInMillis;
        moveTimeoutInMillis = params.moveTimeoutInMillis;
        allPlayers = new ArrayList<>();
        createGrid(params, slots, frame);
        waitingPlayers.addAll(players);
        waitingPlayers.sort((p1, p2) -> p1.compareTo(p2, stoppedPlayers));
        final List<PlayerStats> stats = new ArrayList<>();
        for (int i = 0; i < waitingPlayers.size(); i++) {
            final LocalPlayer player = waitingPlayers.get(i);
            final PlayerStats playerStats = player.getStatistics(i + 1);
            stats.add(playerStats);
        }
        allPlayers.sort((p1, p2) -> p1.compareTo(p2, stoppedPlayers));
        standings = new ArrayList<>(allPlayers);
        notifyAll(new FinalStandings(stats, resultStorage != null));
        current = waitingPlayers.remove(0);
    }

    private void createGrid(Params params, PlayerSlot[] slots, JFrame frame) {
        final Map<AI, ProfileMessage> aiToProfile = new LinkedHashMap<>(); // preserve order
        for (PlayerSlot slot : slots) {
            final ProfileMessage profile = slot.getProfile();
            if (profile != null) {
                if (profile.isAi()) {
                    aiToProfile.put(profile.createAI(data), profile);
                }
                else if (profile.isLocal()) {
                    aiToProfile.put(new ManualAI(profile.createAI(data), frame, this, profile.originalProfile, data), profile);
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
        final List<Node> grid = data.getStartingGrid(playerCount);
        collisionMap = data.getCollisionMap();

        final List<Integer> startingOrder = IntStream.range(0, playerCount).boxed().collect(Collectors.toList());
        if (params.randomizeStartingOrder) {
            Collections.shuffle(startingOrder, rng);
        }
        enableTimeout = true;
        final List<CreatedPlayerNotification> notifications = new ArrayList<>();
        for (Map.Entry<AI, ProfileMessage> e : aiToProfile.entrySet()) {
            notifications.add(createAiPlayer(e, grid, startingOrder, params.leeway, params.laps, params.maxHitpoints));
        }
        aiMap.forEach((player, ai) -> {
            notifications.forEach(notification -> {
                ai.notify(notification.controlled(notification.getPlayerId().equals(player.getId())));
            });
        });
        immutablePlayerMap = new HashMap<>(aiToProfile.size());
        allPlayers.forEach(player -> immutablePlayerMap.put(player.getId(), player));
    }

    private CreatedPlayerNotification createAiPlayer(Map.Entry<AI, ProfileMessage> ai, List<Node> grid, List<Integer> startingOrder, int leeway, int laps, int maxHitpoints) {
        // Recreate track for each player, so nothing bad happens if AI mutates it.
        final int playerCount = allPlayers.size();
        final String playerId = "p" + (playerCount + 1);
        final int gridPosition = startingOrder.get(playerCount);
        final Node startNode = grid.get(gridPosition);
        final LocalPlayer player = new LocalPlayer(playerId, startNode, startNode.getGridAngle(), laps, this, leeway, maxHitpoints, ai.getValue().getColor1(), ai.getValue().getColor2());
        current = player;
        log.info("Initializing player " + playerId);
        final String name = ai.getValue().getName();
        final UUID id = ai.getValue().getId();
        log.info("Initialization done, player " + name + " starts from position " + (startingOrder.get(playerCount) + 1));
        player.setName(name);
        player.setId(id);
        players.add(player);
        allPlayers.add(player);
        player.setGridPosition(gridPosition + 1);
        aiMap.put(player, ai.getKey());
        return new CreatedPlayerNotification(current.getId(), name, startNode.getId(), maxHitpoints, laps, ai.getValue().getColor1(), ai.getValue().getColor2(), startNode.getGridAngle());
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
        while (!stopped) {
            current.beginTurn();
            final AI ai = aiMap.get(current);
            final GameState gameState = ApiHelper.buildGameState(data.getTrackId(), allPlayers);
            log.info("Querying gear input from AI " + current.getNameAndId());
            final Gear gearResponse = getAiInput(() -> ai.selectGear(gameState), gearTimeoutInMillis);
            if (ai instanceof ManualAI || allPlayers.stream().filter(pl -> !pl.isStopped()).map(aiMap::get).noneMatch(p -> p instanceof ManualAI)) {
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
            repaint();
            final Moves allMoves = current.findAllTargets(roll, data.getTrackId(), players);
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
                current.move(selectedIndex);
                current.collide(players, collisionMap, rng);
                if (roll == 20 || roll == 30) {
                    LocalPlayer.possiblyAddEngineDamage(players, rng);
                }
            }
            roll = null;
            nextPlayer();
            repaint();
        }
        if (!interrupted) {
            final List<PlayerStats> stats = new ArrayList<>();
            for (int i = 0; i < stoppedPlayers.size(); i++) {
                final LocalPlayer player = stoppedPlayers.get(i);
                final PlayerStats playerStats = player.getStatistics(i + 1);
                stats.add(playerStats);
            }
            final FinalStandings fs = new FinalStandings(stats, resultStorage != null);
            if (resultStorage != null) {
                resultStorage.updateResult(fs);
            }
            finalStandings = fs.getStats();
            notifyAll(fs);
            if (lobby != null) {
                lobby.close();
            }
            repaint();
            clickToExit();
        }
    }

    @Override
    protected void exit() {
        // This will terminate loop waiting for player input
        stopped = true;
        interrupted = true;
        aiMap.values().forEach(ai -> {
            if (ai instanceof ManualAI) {
                ((ManualAI) ai).interrupted = true;
            }
        });
        super.exit();
    }

    public static boolean validateTrack(String trackId, boolean external) {
        if (external && !new File(trackId).exists()) {
            log.log(Level.WARNING, "Track validation failed: External data file " + trackId + " not found");
            return false;
        }
        final Level errorLevel = external ? Level.WARNING : Level.SEVERE;
        final List<Node> nodes = new ArrayList<>();
        final Map<Node, Double> attributes = new HashMap<>();
        try (InputStream is = external ? new FileInputStream(trackId) : Main.class.getResourceAsStream("/" + trackId)) {
            final Pair<String, MapEditor.Corner> result = MapEditor.loadNodes(is, nodes, attributes);
            if (result == null) {
                log.log(errorLevel, "Track validation failed: Proper header is missing from " + trackId);
                return false;
            }
            final List<Node> grid = TrackData.build(nodes, attributes);
            if (grid.size() < 10) {
                log.log(errorLevel, "Track validation failed: Starting grid has less than 10 spots");
                return false;
            }
            final String imageFile = result.getLeft();
            if (external && (!new File(imageFile).exists() || Files.size(Paths.get(imageFile)) > 5_000_000)) {
                log.log(errorLevel, "Track validation failed: External background image " + imageFile + " not found or too big");
                return false;
            }
            final BufferedImage image = external ? ImageCache.getImageFromPath(imageFile) : ImageCache.getImage("/" + imageFile);
            if (image == null) {
                log.log(errorLevel, "Track validation failed: Background image " + imageFile + " not found");
                return false;
            }
            TrackLanes.buildCollisionMap(nodes);
        } catch (IOException e) {
            log.log(errorLevel, "Track validation failed: " + e.getMessage(), e);
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            log.log(errorLevel, "Track validation failed for " + trackId + ": " + e.getMessage(), e);
            //e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (debug) {
            drawDistances((Graphics2D) g);
        }
    }

    static String getDistanceString(double distance) {
        final int distanceInt = (int) (100 * distance + 0.5);
        final int part1 = distanceInt / 100;
        int part2 = distanceInt % 100;
        if (part2 % 10 == 0) part2 = part2 / 10;
        else if (part2 < 10) return part1 + ".0" + part2;
        return part1 + "." + part2;
    }

    private void drawDistances(Graphics2D g2d) {
        for (Node node : nodes) {
            final Point p = node.getLocation();
            final int posX = p.x - 5;
            final int posY = p.y + 3;
            g2d.setFont(new Font("Arial", Font.PLAIN, 8));
            g2d.setColor(Color.BLUE);
            g2d.drawString(getDistanceString(node.getDistance()), posX, posY);

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
                stoppedPlayers.sort((p1, p2) -> p1.compareTo(p2, clone));
                // This will make the game thread to stop.
                stopped = true;
                return;
            }
            // Set turn order for next round
            waitingPlayers.addAll(players);
            waitingPlayers.sort((p1, p2) -> p1.compareTo(p2, stoppedPlayers));
            // Sort info box contents to match with current standings and turn order
            allPlayers.sort((p1, p2) -> p1.compareTo(p2, stoppedPlayers));
            standings = new ArrayList<>(allPlayers);
            notifyAll(new Standings(allPlayers));
        }
        previous = current;
        current = waitingPlayers.remove(0);
    }

    static class Params {
        private Params() {
        }
        Params(int laps, int animationDelayMs, int timePerTurnMs, int leewayMs, int maxHitpoints) {
            this.laps = laps;
            this.animationDelayInMillis = animationDelayMs;
            this.gearTimeoutInMillis = timePerTurnMs;
            this.moveTimeoutInMillis = timePerTurnMs;
            this.leeway = leewayMs;
            this.maxHitpoints = maxHitpoints;
        }
        private int animationDelayInMillis = 100;
        private int gearTimeoutInMillis = 3000;
        private int moveTimeoutInMillis = 3000;
        private int leeway = 3600000;
        private Long seed = null;
        private boolean randomizeStartingOrder = false;
        private int laps = 1;
        private int maxHitpoints = 18;
    }

    private static void showGameSettings(JFrame frame, JPanel panel, Lobby lobby, List<Profile> profiles, Params params, WindowChanger listener) {
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
        final TrackData previousTrack = TrackData.createTrackData(settings.trackId, settings.external);
        if (previousTrack == null) {
            return;
        }
        changeTrackButton.setTrack(previousTrack, TrackPreviewButton.createIcon(previousTrack), settings.laps);
        final JPanel lobbyPanel = new JPanel();
        lobbyPanel.setName(lobby == null ? "Game Settings" : "Multiplayer Game Settings");
        lobbyPanel.setLayout(new BoxLayout(lobbyPanel, BoxLayout.PAGE_AXIS));
        lobbyPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        final JButton startButton = new JButton("Start");
        final JCheckBox randomStartingOrder = new JCheckBox("Randomize starting order", settings.randomStartOrder);
        final SettingsField laps = new SettingsField(lobbyPanel, "Laps", Integer.toString(settings.laps), 1, 200);
        final SettingsField hitpoints = new SettingsField(lobbyPanel, "Hitpoints", Integer.toString(settings.maxHitpoints), 1, 30);
        final SettingsField animationDelay = new SettingsField(lobbyPanel, "Animation delay (ms)", Integer.toString(settings.animationDelay), 0, 1000);
        final SettingsField time = new SettingsField(lobbyPanel, "Time per turn (s)", Integer.toString(settings.timePerTurn), 0, 3600);
        final SettingsField leeway = new SettingsField(lobbyPanel, "Time leeway (s)", Integer.toString(settings.leeway), 0, 36000);
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
                params.gearTimeoutInMillis = time.getValue() * 1000;
                params.moveTimeoutInMillis = time.getValue() * 1000;
                params.leeway = leeway.getValue() * 1000;
                params.maxHitpoints = hitpoints.getValue();
            } catch (NumberFormatException ex) {
                return;
            }
            if (lobby != null) {
                lobby.done = true;
                lobby.interrupt();
            }
            params.randomizeStartingOrder = randomStartingOrder.isSelected();
            settings.randomStartOrder = params.randomizeStartingOrder;
            settings.laps = params.laps;
            settings.animationDelay = params.animationDelayInMillis;
            settings.timePerTurn = time.getValue();
            settings.leeway = leeway.getValue();
            settings.maxHitpoints = params.maxHitpoints;
            final Main server = new Main(params, lobby, frame, panel, slots, changeTrackButton.getTrackData(), null);
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
        hitpoints.setAlignmentX(Component.LEFT_ALIGNMENT);
        animationDelay.setAlignmentX(Component.LEFT_ALIGNMENT);
        time.setAlignmentX(Component.LEFT_ALIGNMENT);
        leeway.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(randomStartingOrder);
        settings.add(laps);
        settings.add(hitpoints);
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

    static void setContent(JFrame f, JPanel p) {
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
        f.setSize(Math.min(screenSize.width, f.getWidth()), Math.min(screenSize.height - 100, f.getHeight()));
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

    private static void showMenu(JFrame f, List<Profile> profiles) {
        final JPanel p = new JPanel();
        f.setMenuBar(new MainMenuBar(f, p));
        final WindowChanger listener = new WindowChanger(f, p);
        f.addWindowListener(listener);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        final JPanel title = new JPanel();
        final JLabel titleText = new JLabel("GP ONLINE");
        titleText.setFont(new Font("Arial", Font.BOLD, 32));
        title.add(titleText);
        final JPanel contents = new JPanel(new GridLayout(0, 2));
        p.add(title);
        p.setName("Main Menu");
        p.add(contents);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        final JPanel buttonPanel = new JPanel(new GridLayout(5, 0));
        final ProfilePanel profilePanel = new ProfilePanel(profiles);
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contents.add(buttonPanel);
        contents.add(profilePanel);
        Params params = new Params();

        final JButton singlePlayerButton = new JButton("Single Race");
        singlePlayerButton.addActionListener(e -> {
            showGameSettings(f, p, null, profiles, params, listener);
        });
        final JButton championshipButton = new JButton("Championship Season");
        championshipButton.addActionListener(e -> {
            final DefaultListModel<String> model = new DefaultListModel<>();
            final File file = new File(".");
            final File[] dataFiles = file.listFiles(fn -> fn.getName().toLowerCase().endsWith(".cha") && fn.getName().length() > 4);
            final Map<String, Season> existingSeasons = new HashMap<>();
            if (dataFiles != null) {
                for (File fn : dataFiles) {
                    final String name = fn.getName().substring(0, fn.getName().length() - 4);
                    final Season season = new Season(f, name);
                    if (season.load()) {
                        existingSeasons.put(name, season);
                    }
                }
                existingSeasons.values().stream().sorted(Season::compareTo).map(Season::getName).forEach(model::addElement);
            }
            final JDialog dialog = new JDialog(f);
            final JList<String> list = new JList<>(model);
            final JButton newButton = new JButton("New");
            final JButton templateButton = new JButton("Template");
            final JButton selectButton = new JButton("Load");
            final JButton deleteButton = new JButton("Delete");
            newButton.addActionListener(e1 -> {
                String result = (String) JOptionPane.showInputDialog(dialog, "New season name", "New season", JOptionPane.PLAIN_MESSAGE, null, null, null);
                if (result != null && !result.isEmpty()) {
                    if (existingSeasons.containsKey(result)) {
                        JOptionPane.showConfirmDialog(dialog, "Season with name " + result + " already exists", "Error", JOptionPane.DEFAULT_OPTION);
                    } else {
                        final Season season = new Season(f, result);
                        dialog.setVisible(false);
                        season.start(profiles, null, null, listener);
                    }
                }
            });
            templateButton.addActionListener(new ActionListener() {
                private final List<Pair<String, Integer>> tracksAndLaps = new ArrayList<>();
                private final List<ProfileMessage> profileMessages = new ArrayList<>();

                private int stringToInt(String str) {
                    if (str.startsWith("0x")) {
                        return Integer.parseInt(str.substring(2), 16);
                    } else if (str.startsWith("#")) {
                        return Integer.parseInt(str.substring(1), 16);
                    } else {
                        return Integer.parseInt(str);
                    }
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    final JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setDialogTitle("Load Championship Season Template");
                    fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
                    fileChooser.addChoosableFileFilter(new MapEditor.Filter(MapEditor.Filter.templateExtensions, "Championship Season Template"));
                    fileChooser.setAcceptAllFileFilterUsed(true);
                    final int choice = fileChooser.showOpenDialog(dialog);
                    if (choice == JFileChooser.APPROVE_OPTION) {
                        final File selectedFile = fileChooser.getSelectedFile();
                        String line = null;
                        try (FileInputStream fis = new FileInputStream(selectedFile);
                             InputStreamReader ir = new InputStreamReader(fis, StandardCharsets.UTF_8);
                             final BufferedReader br = new BufferedReader(ir)) {
                            final Set<String> players = new HashSet<>();
                            final Random random = new Random();
                            int emptyLines = 0;
                            while ((line = br.readLine()) != null) {
                                final String[] parts = line.trim().split(",");
                                if (parts.length == 1 && parts[0].isEmpty()) {
                                    ++emptyLines;
                                } else if (emptyLines == 0) {
                                    final String trackId = parts[0];
                                    if (tracksAndLaps.stream().noneMatch(t -> t.getLeft().equals(trackId))) {
                                        int laps = parts.length > 1 ? Integer.parseInt(parts[1]) : Main.settings.laps;
                                        if (laps <  1 || laps > 200) laps = Main.settings.laps;
                                        tracksAndLaps.add(Pair.of(trackId, laps));
                                    }
                                } else {
                                    final String name = parts[0];
                                    if (players.add(name)) {
                                        final Profile profile = profiles.stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
                                        final ProfileMessage profileMessage;
                                        if (profile == null) {
                                            final String ai = parts.length > 1 ? AI.Type.valueOf(parts[1]).toString() : AI.Type.PRO.toString();
                                            final int color1 = parts.length > 2 ? stringToInt(parts[2]) : random.nextInt(0xFFFFFF + 1);
                                            final int color2 = parts.length > 3 ? stringToInt(parts[3]) : random.nextInt(0xFFFFFF + 1);
                                            String[] msg = new String[] { UUID.randomUUID().toString(), name, Integer.toString(color1), Integer.toString(color2), "true", ai };
                                            profileMessage = ProfileMessage.readProfile(msg);
                                        } else {
                                            profileMessage = new ProfileMessage(profile);
                                            if (parts.length > 1) {
                                                profileMessage.setAIType(AI.Type.valueOf(parts[1]));
                                            }
                                        }
                                        profileMessages.add(profileMessage);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            JOptionPane.showConfirmDialog(dialog, "Invalid file format: " + selectedFile.getName() + "(" + ex.getMessage() + (line == null ? "" : " - " + line + ")"), "File Format Error", JOptionPane.DEFAULT_OPTION);
                            return;
                        }
                    } else {
                        return;
                    }
                    if (tracksAndLaps.size() < 3) {
                        JOptionPane.showConfirmDialog(dialog, "Invalid template: Less than 3 tracks", "Invalid Template", JOptionPane.DEFAULT_OPTION);
                        return;
                    }
                    final String result = (String) JOptionPane.showInputDialog(dialog, "New season name", "New season", JOptionPane.PLAIN_MESSAGE, null, null, null);
                    if (result != null && !result.isEmpty()) {
                        if (existingSeasons.containsKey(result)) {
                            JOptionPane.showConfirmDialog(dialog, "Season with name " + result + " already exists", "Error", JOptionPane.DEFAULT_OPTION);
                        } else {
                            final Season season = new Season(f, result);
                            dialog.setVisible(false);
                            season.start(profiles, tracksAndLaps, profileMessages, listener);
                        }
                    }
                }
            });
            selectButton.addActionListener(e12 -> {
                final int index = list.getSelectedIndex();
                if (index == -1) {
                    return;
                }
                final String selectedSeason = model.getElementAt(index);
                final Season season = existingSeasons.get(selectedSeason);
                if (season != null) {
                    season.updateProfileInfo(profiles);
                    dialog.setVisible(false);
                    season.showStandings(listener);
                }
            });
            deleteButton.addActionListener(e13 -> {
                final int index = list.getSelectedIndex();
                if (index == -1) {
                    return;
                }
                final String deletedSeason = model.getElementAt(index);
                int result = JOptionPane.showConfirmDialog(dialog, "Are you sure you want to delete season " + deletedSeason, "Confirm", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    final Season season = existingSeasons.get(deletedSeason);
                    if (season != null && season.delete()) {
                        existingSeasons.remove(deletedSeason);
                        model.remove(index);
                        list.setSelectedIndex(-1);
                    }
                }
            });
            selectButton.setEnabled(!model.isEmpty());
            deleteButton.setEnabled(!model.isEmpty());

            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setSelectedIndex(0); // most recent season
            list.addListSelectionListener(e14 -> {
                if (!e14.getValueIsAdjusting()) {
                    if (list.getSelectedIndex() == -1) {
                        deleteButton.setEnabled(false);
                        selectButton.setEnabled(false);
                    } else {
                        deleteButton.setEnabled(true);
                        selectButton.setEnabled(true);
                    }
                }
            });
            list.setVisibleRowCount(5);
            final JScrollPane listScrollPane = new JScrollPane(list);

            final JPanel buttonPane = new JPanel();
            buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.Y_AXIS));
            buttonPane.add(newButton);
            buttonPane.add(templateButton);
            buttonPane.add(selectButton);
            buttonPane.add(deleteButton);
            buttonPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

            final JPanel contents2 = new JPanel(new GridLayout(0, 2));
            contents2.add(listScrollPane, BorderLayout.CENTER);
            contents2.add(buttonPane, BorderLayout.PAGE_END);
            dialog.setTitle("Manage Championship Seasons");
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setContentPane(contents2);
            dialog.pack();
            dialog.setModal(true);
            dialog.setLocationRelativeTo(f);
            dialog.setVisible(true);
        });

        final JButton hostMultiplayerButton = new JButton("Host Multiplayer");
        hostMultiplayerButton.addActionListener(e -> {
            String result = (String) JOptionPane.showInputDialog(f, "Select port", "Select port", JOptionPane.PLAIN_MESSAGE,  null, null, settings.port);
            if (result == null) {
                return;
            }
            try {
                final int port = Integer.parseInt(result);
                settings.port = result;
                final Lobby lobby = new Lobby(port);
                showGameSettings(f, p, lobby, profiles, params, listener);
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
            String result = (String) JOptionPane.showInputDialog(p, "IP address and port", "IP address and port", JOptionPane.PLAIN_MESSAGE,  null, null, settings.server);
            if (result == null) {
                return;
            }
            String[] addressAndPort = result.split(":");
            try {
                if (addressAndPort.length == 2) {
                    final int port = Integer.parseInt(addressAndPort[1]);
                    Socket socket = new Socket(addressAndPort[0], port);
                    settings.server = result;
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
            final MenuBar oldBar = f.getMenuBar();
            final MapEditor editor = new MapEditor(f);
            if (editor.open()) {
                listener.contentChanged(editor, null, null, "track editor", true);
                setContent(f, editor);
            } else {
                f.setMenuBar(oldBar);
            }
        });
        singlePlayerButton.setFont(new Font("Arial", Font.BOLD, 20));
        championshipButton.setFont(new Font("Arial", Font.BOLD, 20));
        hostMultiplayerButton.setFont(new Font("Arial", Font.BOLD, 20));
        joinMultiplayerButton.setFont(new Font("Arial", Font.BOLD, 20));
        trackEditorButton.setFont(new Font("Arial", Font.BOLD, 20));
        singlePlayerButton.setPreferredSize(new Dimension(80, 40));
        championshipButton.setPreferredSize(new Dimension(80, 40));
        hostMultiplayerButton.setPreferredSize(new Dimension(80, 40));
        joinMultiplayerButton.setPreferredSize(new Dimension(80, 40));
        trackEditorButton.setPreferredSize(new Dimension(80, 40));
        buttonPanel.add(singlePlayerButton);
        buttonPanel.add(championshipButton);
        buttonPanel.add(hostMultiplayerButton);
        buttonPanel.add(joinMultiplayerButton);
        buttonPanel.add(trackEditorButton);
        f.setContentPane(p);
        f.pack();
        f.addWindowListener(new ProfileSaver(f, profiles));
        f.setVisible(true);
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("ide")) {
            ide = true;
        }
        final JFrame f = new JFrame();
        //f.setResizable(false);
        final Profile.Manager profileManager = new Profile.Manager();
        final List<Profile> profiles = new ArrayList<>();
        try {
            final FileInputStream fileIn = new FileInputStream("profiles.sav");
            final ObjectInputStream objectIn = new ObjectInputStream(fileIn);
            while (true) {
                final Object obj = objectIn.readObject();
                if (obj == null) {
                    break;
                } else if (obj instanceof Profile) {
                    final Profile profile = (Profile) obj;
                    //System.err.println(profile.getName() + " has " + profile.getResults().size() + " results");
                    //System.err.println(profile.getResults().stream().filter(Profile.Result::isChampionshipRace).count());
                    //System.err.println(profile.getResults().stream().filter(Profile.Result::isComplete).count());
                    profiles.add(profile);
                    profile.setManager(profileManager);
                }
            }
            objectIn.close();
        } catch (IOException | ClassNotFoundException e) {
            if (profiles.isEmpty()) {
                final Profile defaultProfile = new Profile(profileManager, "Player");
                defaultProfile.setActive(true);
                defaultProfile.setColor1(defaultColor1);
                defaultProfile.setColor2(defaultColor2);
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
        f.setIconImages(ImageCache.getCarIcons());
        /* For mac build only
        if (Application.getApplication() != null) {
            Application.getApplication().setDockIconImage(f.getIconImage());
        }*/
        showMenu(f, profiles);
    }

    @Override
    protected LocalPlayer getCurrent() {
        return current;
    }
}
