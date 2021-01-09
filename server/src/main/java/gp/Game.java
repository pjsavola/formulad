package gp;

import gp.ai.Node;
import gp.ai.TrackData;
import gp.model.GameState;
import gp.model.HitpointNotification;
import gp.model.PlayerStats;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.logging.Level;

public abstract class Game extends JPanel implements PlayerRenderer {
    // Parent JFrame and main menu JPanel, used when returning back to the main menu after the race is over.
    final JFrame frame;
    private final JPanel panel;
    public final Menu actionMenu;
    public final Menu drivingAids;

    private BufferedImage backgroundImage;

    // Node identifier equals to the index in this array
    final List<Node> nodes = new ArrayList<>();

    // Used when selecting where to move. Maps node index to damage taken if that node is selected.
    private Map<Integer, Integer> highlightedNodeToDamage;
    private int mouseOverHighlightNodeIndex = -1;

    Integer roll;
    List<Player> standings;
    Map<String, Player> immutablePlayerMap;

    private Map<String, Integer> hitpointMap = new HashMap<>();

    PlayerStats[] finalStandings;

    private static Font damageFont = new Font("Arial", Font.PLAIN, 9);
    private static Font bigDamageFont = new Font("Arial", Font.BOLD, 11);
    private static Font titleFont = new Font("Arial", Font.BOLD, 20);
    static Font headerFont = new Font("Arial", Font.BOLD, 12);
    private static Font statsFont = new Font("Arial", Font.PLAIN, 12);

    TrackData data;
    private MapEditor.Corner infoBoxCorner;
    private MapEditor.Corner gearCorner;
    private volatile boolean keepAlive = true;

    public boolean debug;

    private Timer timer = new Timer();
    private List<HitpointAnimation> animations = Collections.synchronizedList(new ArrayList<>());

    private double scale = 1.0;
    private Dimension panelDim;

    private class HitpointAnimation {
        private final Color color;
        private final int x;
        private final int y;
        private final int thickness;
        private int size;
        private HitpointAnimation(int loss, int x, int y) {
            color = loss > 0 ? Color.RED : Color.GREEN;
            thickness = Math.abs(loss);
            this.x = x;
            this.y = y;
        }
        private boolean increaseSize() {
            if (size > 30) {
                return false;
            } else {
                size += 4;
                repaint();
                return true;
            }
        }
        private void draw(Graphics2D g) {
            MapEditor.drawOval(g, x, y, size + thickness, size + thickness, false, color, thickness);
        }
    }

    Game(JFrame frame, JPanel panel) {
        this.frame = frame;
        this.panel = panel;
        actionMenu = new Menu("Action");
        final MenuBar menuBar = new MenuBar();
        menuBar.add(actionMenu);
        final Menu view = new Menu("View");
        menuBar.add(view);
        drivingAids = new Menu("Aids");
        menuBar.add(drivingAids);
        final MenuItem zoomIn = new MenuItem("Zoom In");
        final MenuItem zoomOut = new MenuItem("Zoom Out");
        view.add(zoomIn);
        view.add(zoomOut);
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        zoomIn.addActionListener(e -> {
            final double oldScale = scale;
            scale = Math.min(4.0, scale * 1.1);
            if (scale != oldScale) {
                setPreferredSize(new Dimension((int) (backgroundImage.getWidth() * scale), (int) (backgroundImage.getHeight() * scale)));
                frame.pack();
                frame.setSize(Math.min(screenSize.width, frame.getWidth()), Math.min(screenSize.height - 100, frame.getHeight()));
            }
        });
        zoomIn.setShortcut(new MenuShortcut(KeyEvent.VK_PLUS));
        zoomOut.addActionListener(e -> {
            final double oldScale = scale;
            scale = Math.max(1.0, scale / 1.1);
            if (scale != oldScale) {
                setPreferredSize(new Dimension((int) (backgroundImage.getWidth() * scale), (int) (backgroundImage.getHeight() * scale)));
                frame.pack();
                frame.setSize(Math.min(screenSize.width, frame.getWidth()), Math.min(screenSize.height - 100, frame.getHeight()));
            }
        });
        zoomOut.setShortcut(new MenuShortcut(KeyEvent.VK_MINUS));
        final Menu sounds = new Menu("Sound");
        menuBar.add(sounds);
        final String off = "Sound OFF";
        final String on = "Sound ON";
        final MenuItem soundToggle = new MenuItem(Main.sounds ? on : off);
        soundToggle.addActionListener(e -> {
            Main.sounds = !Main.sounds;
            soundToggle.setLabel(Main.sounds ? on : off);
        });
        sounds.add(soundToggle);
        frame.setMenuBar(menuBar);
        new Thread(() -> {
            try {
                Robot hal = new Robot();
                while (keepAlive) {
                    hal.delay(1000 * 30);
                    Point pObj = MouseInfo.getPointerInfo().getLocation();
                    hal.mouseMove(pObj.x + 1, pObj.y + 1);
                    hal.mouseMove(pObj.x - 1, pObj.y - 1);
                }
            } catch (AWTException e) {
                Main.log.log(Level.WARNING, "Failed to start keep-alive thread", e);
            }
        }).start();
    }

    void initTrack(TrackData data) {
        this.data = data;
        infoBoxCorner = data.getInfoBoxCorner();
        nodes.clear();
        nodes.addAll(data.getNodes());
        backgroundImage = data.getBackgroundImage();
        panelDim = new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight());
        setPreferredSize(panelDim);
        frame.pack();
        // Figure out best corner for gear rolls
        final List<MapEditor.Corner> possibleCorners = new ArrayList<>();
        possibleCorners.add(MapEditor.Corner.NW);
        possibleCorners.add(MapEditor.Corner.NE);
        possibleCorners.add(MapEditor.Corner.SW);
        possibleCorners.add(MapEditor.Corner.SE);
        possibleCorners.remove(infoBoxCorner);
        for (Node node : nodes) {
            final int x = node.getLocation().x;
            final int y = node.getLocation().y;
            Iterator<MapEditor.Corner> it = possibleCorners.iterator();
            while (it.hasNext()) {
                final Point point = getPoint(it.next());
                if (point == null) continue;
                final int dx = x - point.x;
                final int dy = y - point.y;
                if (dx * dx + dy * dy < 35 * 35) {
                    it.remove();
                }
            }
        }
        gearCorner = possibleCorners.isEmpty() ? MapEditor.Corner.NW : possibleCorners.get(0);
    }

    private Point getPoint(MapEditor.Corner corner) {
        switch (corner) {
            case NW: return new Point(40, 40);
            case NE: return new Point(panelDim.width - 40, 40);
            case SW: return new Point(40, panelDim.height - 40);
            case SE: return new Point(panelDim.width - 40, panelDim.height -  40);
        }
        return null;
    }

    void clickToExit() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                removeMouseListener(this);
                exit();
            }
        });
    }

    protected void exit() {
        keepAlive = false;
        for (WindowListener listener : frame.getWindowListeners()) {
            if (listener instanceof WindowChanger) {
                ((WindowChanger) listener).reset(panel);
            }
        }
        frame.setMenuBar(new MainMenuBar(frame, panel));
        frame.setContentPane(panel);
        frame.pack();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        AffineTransform at = new AffineTransform();
        at.scale(scale, scale);
        ((Graphics2D) g).transform(at);
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
        // Circle for dice rolls
        if (gearCorner != null) {
            final Point point = getPoint(gearCorner);
            assert point != null;
            MapEditor.drawOval(g2d, point.x, point.y, 50, 50, true, Color.BLACK, 1);
        }
        drawTargets(g2d);
        drawInfoBox(g2d);
        drawRetiredPlayers(g2d);
        drawPlayers(g2d);
        drawStandings(g2d);
        animations.forEach(a -> a.draw(g2d));
    }

    private void drawTargets(Graphics2D g2d) {
        if (highlightedNodeToDamage != null) {
            for (Map.Entry<Integer, Integer> entry : highlightedNodeToDamage.entrySet()) {
                final int nodeId = entry.getKey();
                if (nodeId < 0 || nodeId >= nodes.size()) continue;
                final Node node = nodes.get(nodeId);
                final int damage = entry.getValue();
                final Point p = node.getLocation();
                if (nodeId == mouseOverHighlightNodeIndex) {
                    MapEditor.drawOval(g2d, p.x, p.y, 15, 15, true, Color.YELLOW, 1);
                    if (damage != 0) {
                        g2d.setFont(bigDamageFont);
                        g2d.setColor(Color.BLACK);
                        final String str = Integer.toString(damage);
                        final int w = g2d.getFontMetrics().stringWidth(str);
                        final int x = p.x - w / 2;
                        g2d.drawString(str, x, p.y + 4);
                    }
                } else {
                    MapEditor.drawOval(g2d, p.x, p.y, 12, 12, false, Color.YELLOW, 1);
                    if (damage != 0) {
                        g2d.setFont(damageFont);
                        g2d.setColor(Color.RED);
                        final String str = Integer.toString(damage);
                        final int w = g2d.getFontMetrics().stringWidth(str);
                        final int x = p.x - w / 2;
                        g2d.drawString(str, x, p.y + 3);
                    }
                }
            }
        }
    }

    private void drawInfoBox(Graphics2D g2d) {
        // Other thread may replace this.standings with a new object, but it's not mutated
        final List<Player> standings = this.standings;
        if (standings == null) return;
        UIUtil.drawInfoBox(g2d, panelDim, standings.size(), infoBoxCorner);
        int i = 0;
        for (Player player : standings) {
            if (player == getCurrent()) {
                UIUtil.drawTurnMarker(g2d, panelDim, standings.size(), infoBoxCorner, i);
            }
            final int x = UIUtil.getX(infoBoxCorner, panelDim, 280);
            final int y = UIUtil.getY(infoBoxCorner, panelDim, 5 + 15 * standings.size());
            player.draw(g2d, x + 15, y + i * 15 + 10, 0);
            player.drawStats(g2d, x + 30, y + i * 15 + 15, hitpointMap);
            ++i;
        }
    }

    private void drawRetiredPlayers(Graphics2D g2d) {
        if (immutablePlayerMap == null) return;

        immutablePlayerMap.values().forEach(player -> player.drawRetired(g2d));
    }

    private void drawPlayers(Graphics2D g2d) {
        if (immutablePlayerMap == null) return;

        final Player current = getCurrent();
        if (current != null) {
            final Integer roll = this.roll;
            if (roll != null) {
                final Point point = getPoint(gearCorner);
                current.drawRoll(g2d, roll, point);
            } else {
                // TODO: Highlight only manual AI?
                immutablePlayerMap.values().stream().filter(p -> p == current && !p.isStopped()).forEach(p -> p.highlight(g2d));
            }
        }
        immutablePlayerMap.values().forEach(player -> player.draw(g2d));
    }

    @Override
    public void renderPlayer(Graphics2D g2d, int x, int y, int i, String playerId) {
        final Player player = immutablePlayerMap.get(playerId);
        final String name = player.getName();
        g2d.drawString(name, x + 30, y + (i + 1) * 15 + 15);
        player.draw(g2d, x + 15, y + (i + 1) * 15 + 10, 0);
    }

    static void drawStandings(Graphics2D g2d, int x, int y, PlayerStats[] finalStandings, PlayerRenderer renderer, double maxDistance) {
        final int height = 5 + 15 * (finalStandings.length + 1);
        g2d.setColor(Color.BLACK);
        g2d.setFont(titleFont);
        final int titleWidth = g2d.getFontMetrics().stringWidth("STANDINGS");
        g2d.drawString("STANDINGS", x + 220 - titleWidth / 2, y - 20);
        g2d.setColor(Color.GRAY);
        g2d.fillRect(x, y, 440, height);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(x, y, 440, height);
        g2d.setColor(Color.BLACK);
        g2d.setFont(headerFont);
        g2d.drawString("Name", x + 30, y + 15);
        g2d.drawString("HP", x + 140, y + 15);
        g2d.drawString("Turns", x + 190, y + 15);
        g2d.drawString("Grid", x + 230, y + 15);
        g2d.drawString("Time", x + 270, y + 15);
        g2d.drawString("Stops", x + 310, y + 15);
        g2d.drawString("Laps", x + 350, y + 15);
        g2d.drawString("Distance", x + 390, y + 15);
        g2d.setFont(statsFont);
        for (int i = 0; i < finalStandings.length; ++i) {
            final PlayerStats stats = finalStandings[i];
            g2d.setColor(Color.BLACK);
            renderer.renderPlayer(g2d, x, y, i, stats.playerId);
            final String hp = stats.hitpoints > 0 ? Integer.toString(stats.hitpoints) : "DNF";
            g2d.drawString(hp, x + 140, y + (i + 1) * 15 + 15);
            g2d.drawString(Integer.toString(stats.turns), x + 190, y + (i + 1) * 15 + 15);
            g2d.drawString(Integer.toString(stats.gridPosition), x + 230, y + (i + 1) * 15 + 15);
            final long timeUsed = stats.timeUsed / 100;
            final double timeUsedSecs = timeUsed / 10.0;
            g2d.drawString(Double.toString(timeUsedSecs), x + 270, y + (i + 1) * 15 + 15);
            g2d.drawString(Integer.toString(stats.pitStops), x + 310, y + (i + 1) * 15 + 15);
            if (stats.lapsToGo >= 0) {
                g2d.drawString(Integer.toString(stats.lapsToGo), x + 350, y + (i + 1) * 15 + 15);
                g2d.drawString(Main.getDistanceString(stats.distance * 100 / (maxDistance + 0.5)) + "%", x + 390, y + (i + 1) * 15 + 15);
            }
        }
    }

    protected void drawStandings(Graphics2D g2d) {
        if (finalStandings != null) {
            final int height = 5 + 15 * (finalStandings.length + 1);
            final int x = panelDim.width / 2 - 220;
            final int y = panelDim.height / 2 - height / 2;
            final double maxDistance = nodes.stream().mapToDouble(Node::getDistance).max().orElse(0);
            drawStandings(g2d, x, y, finalStandings, this, maxDistance);
        }
    }

    /**
     * Sets nodes for highlighting, may be useful when rendering valid targets.
     */
    public void highlightNodes(@Nullable Map<Integer, Integer> nodeToDamage) {
        this.highlightedNodeToDamage = nodeToDamage;
        repaint();
    }

    public void setMouseOverHighlightNodeIndex(int nodeId) {
        if (highlightedNodeToDamage != null) {
            int id = highlightedNodeToDamage.containsKey(nodeId) ? nodeId : -1;
            if (id != mouseOverHighlightNodeIndex) {
                mouseOverHighlightNodeIndex = id;
                repaint();
            }
        }
    }

    /**
     * Returns node identifier of the clicked node, or null if there are no nodes
     * at the given coordinates.
     */
    @Nullable
    public Integer getNodeId(int x, int y) {
        final Node target = MapEditor.getNode(nodes, (int) (x / scale), (int) (y / scale), MapEditor.DIAMETER);
        return target == null ? null : target.getId();
    }

    void updateHitpointMap(GameState gameState) {
        hitpointMap.clear();
        gameState.getPlayers().forEach(p -> hitpointMap.put(p.getPlayerId(), p.getHitpoints()));
    }

    void scheduleHitpointAnimation(int loss, Player player, HitpointNotification.Source source) {
        final Point p = player.node.getLocation();
        final HitpointAnimation a = new HitpointAnimation(loss, p.x, p.y);
        animations.add(a);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!a.increaseSize()) {
                    animations.remove(a);
                    repaint();
                }
            }
        }, 0, 50); // 20 FPS
        if (Main.sounds) {
            switch (source) {
                case COLLISION:
                    SoundSystem.playSound(SoundSystem.Type.COLLISION);
                    break;
                case CURVE:
                    break;
                case CRASH:
                    SoundSystem.playSound(SoundSystem.Type.DNF);
                    break;
                case ENGINE:
                    SoundSystem.playSound(SoundSystem.Type.ENGINE);
                    break;
                case GEARS:
                    SoundSystem.playSound(SoundSystem.Type.GEARS);
                    break;
                case PITS:
                    SoundSystem.playSound(SoundSystem.Type.PITS);
                    break;
            }
        }
    }

    protected abstract Player getCurrent();
}
