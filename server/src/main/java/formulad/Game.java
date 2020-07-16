package formulad;

import formulad.ai.Node;
import formulad.model.PlayerStats;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Game extends JPanel {
    // Parent JFrame and main menu JPanel, used when returning back to the main menu after the race is over.
    private final JFrame frame;
    private final JPanel panel;

    private BufferedImage backgroundImage;

    // Node identifier equals to the index in this array
    final List<Node> nodes = new ArrayList<>();
    // Contains coordinates for all nodes
    final Map<Node, Point> coordinates = new HashMap<>();
    // Contains angles for start nodes and distance information for curves
    final Map<Node, Double> attributes = new HashMap<>();

    // Used when selecting where to move. Maps node index to damage taken if that node is selected.
    private Map<Integer, Integer> highlightedNodeToDamage;

    // Current dice roll and player, needed for rendering.
    private Integer roll;
    private Player current;

    PlayerStats[] finalStandings;

    private Font damageFont = new Font("Arial", Font.PLAIN, 9);

    Game(JFrame frame, JPanel panel) {
        this.frame = frame;
        this.panel = panel;
    }

    void initTrack(String trackId) {
        final String dataFile = "/" + trackId + ".dat";
        final String imageFile = "/" + trackId + ".jpg";
        backgroundImage = ImageCache.getImage(imageFile);
        try (InputStream is = Client.class.getResourceAsStream(dataFile)) {
            MapEditor.loadNodes(is, nodes, attributes, coordinates);
        } catch (IOException e) {
            throw new RuntimeException("Data file " + dataFile + " is missing", e);
        }
        setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        frame.setContentPane(this);
        frame.pack();
        repaint();
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

    void exit() {
        frame.setContentPane(panel);
        frame.pack();
        panel.repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
        // Circle for dice rolls
        MapEditor.drawOval(g2d, 40, 40, 50, 50, true, true, Color.BLACK, 1);
        drawTargets(g2d);
        drawInfoBox(g2d);
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

    // TODO: elevate these to this class
    protected abstract void drawInfoBox(Graphics2D g2d);
    protected abstract void drawPlayers(Graphics2D g2d);
    protected abstract void drawStandings(Graphics2D g2d);

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
}
