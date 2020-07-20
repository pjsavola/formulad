package gp;

import gp.ai.Node;
import gp.model.GameState;
import gp.model.PlayerStats;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.logging.Level;

public abstract class Game extends JPanel {
    // Parent JFrame and main menu JPanel, used when returning back to the main menu after the race is over.
    private final JFrame frame;
    private final JPanel panel;
    public final Menu actionMenu;

    private BufferedImage backgroundImage;

    // Node identifier equals to the index in this array
    final List<Node> nodes = new ArrayList<>();
    // Contains coordinates for all nodes
    final Map<Node, Point> coordinates = new HashMap<>();
    // Contains pit lane garage locations and distance information for curves
    final Map<Node, Double> attributes = new HashMap<>();
    // Contains grid starting angles
    final Map<Node, Double> gridAngles = new HashMap<>();

    // Used when selecting where to move. Maps node index to damage taken if that node is selected.
    private Map<Integer, Integer> highlightedNodeToDamage;

    Integer roll;
    List<Player> standings;
    Map<String, Player> immutablePlayerMap;

    private Map<String, Integer> hitpointMap = new HashMap<>();

    PlayerStats[] finalStandings;

    private Font damageFont = new Font("Arial", Font.PLAIN, 9);
    private Font titleFont = new Font("Arial", Font.BOLD, 20);
    private Font headerFont = new Font("Arial", Font.BOLD, 12);
    private Font statsFont = new Font("Arial", Font.PLAIN, 12);

    String trackId;
    private MapEditor.Corner infoBoxCorner;
    private final Thread keepAlive;

    Game(JFrame frame, JPanel panel) {
        this.frame = frame;
        this.panel = panel;
        actionMenu = new Menu("Action");
        final MenuBar menuBar = new MenuBar();
        menuBar.add(actionMenu);
        frame.setMenuBar(menuBar);
        keepAlive = new Thread(() -> {
            Robot hal = null;
            try {
                hal = new Robot();
                while (true) {
                    hal.delay(1000 * 30);
                    Point pObj = MouseInfo.getPointerInfo().getLocation();
                    hal.mouseMove(pObj.x + 1, pObj.y + 1);
                    hal.mouseMove(pObj.x - 1, pObj.y - 1);
                    pObj = MouseInfo.getPointerInfo().getLocation();
                }
            } catch (AWTException e) {
                Main.log.log(Level.WARNING, "Failed to start keep-alive thread", e);
            }
        });
        keepAlive.start();
    }

    void initTrack(String trackId) {
        this.trackId = trackId;
        final String dataFile = "/" + trackId;
        try (InputStream is = Main.class.getResourceAsStream(dataFile)) {
            final Pair<String, MapEditor.Corner> result = MapEditor.loadNodes(is, nodes, attributes, gridAngles, coordinates);
            final String imageFile = "/" + result.getLeft();
            infoBoxCorner = result.getRight();
            backgroundImage = ImageCache.getImage(imageFile);
        } catch (IOException e) {
            throw new RuntimeException("Data file " + dataFile + " is missing or corrupted", e);
        }
        setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        frame.pack();
    }

    void clickToExit() {
        addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                removeMouseListener(this);
                exit();
            }
            @Override
            public void mousePressed(MouseEvent e) {
            }
            @Override
            public void mouseReleased(MouseEvent e) {
            }
            @Override
            public void mouseEntered(MouseEvent e) {
            }
            @Override
            public void mouseExited(MouseEvent e) {
            }
        });
    }

    protected void exit() {
        keepAlive.interrupt();
        for (WindowListener listener : frame.getWindowListeners()) {
            if (listener instanceof WindowChanger) {
                ((WindowChanger) listener).reset();
            }
        }
        frame.setMenuBar(null);
        frame.setContentPane(panel);
        frame.pack();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
        // Circle for dice rolls
        MapEditor.drawOval(g2d, 40, 40, 50, 50, true, true, Color.BLACK, 1);
        drawTargets(g2d);
        drawInfoBox(g2d);
        drawRetiredPlayers(g2d);
        drawPlayers(g2d);
        drawStandings(g2d);
    }

    private void drawTargets(Graphics2D g2d) {
        if (highlightedNodeToDamage != null) {
            for (Map.Entry<Integer, Integer> entry : highlightedNodeToDamage.entrySet()) {
                final int nodeId = entry.getKey();
                if (nodeId < 0 || nodeId >= nodes.size()) continue;
                final Node node = nodes.get(nodeId);
                final int damage = entry.getValue();
                final Point p = coordinates.get(node);
                if (damage > 0) {
                    g2d.setFont(damageFont);
                    g2d.setColor(Color.RED);
                    final int x = p.x - (damage >= 10 ? 5 : 2);
                    g2d.drawString(Integer.toString(damage), x, p.y + 3);
                }
                MapEditor.drawOval(g2d, p.x, p.y, 12, 12, true, false, Color.YELLOW, 1);
            }
        }
    }

    private void drawInfoBox(Graphics2D g2d) {
        // Other thread may replace this.standings with a new object, but it's not mutated
        final List<Player> standings = this.standings;
        if (standings == null) return;
        UIUtil.drawInfoBox(g2d, this, standings.size(), infoBoxCorner);
        int i = 0;
        for (Player player : standings) {
            if (player == getCurrent()) {
                UIUtil.drawTurnMarker(g2d, this, standings.size(), infoBoxCorner, i);
            }
            player.draw(g2d, getWidth() - 235, i * 15 + 10, 0);
            player.drawStats(g2d, getWidth() - 220, i * 15 + 15, hitpointMap);
            ++i;
        }
    }

    private void drawRetiredPlayers(Graphics2D g2d) {
        if (immutablePlayerMap == null) return;

        immutablePlayerMap.values().forEach(player -> player.drawRetired(g2d, coordinates));
    }

    private void drawPlayers(Graphics2D g2d) {
        if (immutablePlayerMap == null) return;

        final Player current = getCurrent();
        if (current != null) {
            final Integer roll = this.roll;
            if (roll != null) {
                current.drawRoll(g2d, roll);
            } else {
                // TODO: Highlight only manual AI?
                immutablePlayerMap.values().stream().filter(p -> p == current && !p.isStopped()).forEach(p -> p.highlight(g2d, coordinates));
            }
        }
        immutablePlayerMap.values().forEach(player -> player.draw(g2d, coordinates));
    }

    protected void drawStandings(Graphics2D g2d) {
        if (finalStandings != null) {
            final int height = 5 + 15 * (finalStandings.length + 1);
            final int x = getWidth() / 2 - 200;
            final int y = getHeight() / 2 - height / 2;
            g2d.setColor(Color.BLACK);
            g2d.setFont(titleFont);
            final int titleWidth = g2d.getFontMetrics().stringWidth("STANDINGS");
            g2d.drawString("STANDINGS", x + 200 - titleWidth / 2, y - 20);
            g2d.setColor(Color.GRAY);
            g2d.fillRect(x, y, 400, height);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x, y, 400, height);
            g2d.setColor(Color.BLACK);
            g2d.setFont(headerFont);
            g2d.drawString("Name", x + 30, y + 15);
            g2d.drawString("HP", x + 140, y + 15);
            g2d.drawString("Turns", x + 190, y + 15);
            g2d.drawString("Grid", x + 230, y + 15);
            g2d.drawString("Time", x + 270, y + 15);
            g2d.drawString("Stops", x + 310, y + 15);
            g2d.drawString("Laps", x + 350, y + 15);
            g2d.setFont(statsFont);
            for (int i = 0; i < finalStandings.length; ++i) {
                final PlayerStats stats = finalStandings[i];
                final Player player = immutablePlayerMap.get(stats.playerId);
                final String name = player.getName();
                player.draw(g2d, x + 15, y + (i + 1) * 15 + 10, 0);
                g2d.setColor(Color.BLACK);
                g2d.drawString(name, x + 30, y + (i + 1) * 15 + 15);
                final String hp = stats.hitpoints > 0 ? Integer.toString(stats.hitpoints) : "DNF";
                g2d.drawString(hp, x + 140, y + (i + 1) * 15 + 15);
                g2d.drawString(Integer.toString(stats.turns), x + 190, y + (i + 1) * 15 + 15);
                g2d.drawString(Integer.toString(stats.gridPosition), x + 230, y + (i + 1) * 15 + 15);
                final long timeUsed = stats.timeUsed / 100;
                final double timeUsedSecs = timeUsed / 10.0;
                g2d.drawString(Double.toString(timeUsedSecs), x + 270, y + (i + 1) * 15 + 15);
                g2d.drawString(Integer.toString(stats.pitStops), x + 310, y + (i + 1) * 15 + 15);
                g2d.drawString(Integer.toString(stats.lapsToGo + 1), x + 350, y + (i + 1) * 15 + 15);
            }
        }
    }

    /**
     * Sets nodes for highlighting, may be useful when rendering valid targets.
     */
    public void highlightNodes(@Nullable Map<Integer, Integer> nodeToDamage) {
        this.highlightedNodeToDamage = nodeToDamage;
        repaint();
    }

    /**
     * Returns node identifier of the clicked node, or null if there are no nodes
     * at the given coordinates.
     */
    @Nullable
    public Integer getNodeId(int x, int y) {
        final Node target = MapEditor.getNode(nodes, coordinates, x, y, MapEditor.DIAMETER);
        return target == null ? null : target.getId();
    }

    void updateHitpointMap(GameState gameState) {
        hitpointMap.clear();
        gameState.getPlayers().forEach(p -> {
            hitpointMap.put(p.getPlayerId(), p.getHitpoints());
        });
    }

    protected abstract Player getCurrent();
}
