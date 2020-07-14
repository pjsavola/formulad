package formulad;

import formulad.ai.Gear;
import formulad.ai.Node;
import formulad.model.*;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class Player {
    private static int colorIndex = 0;
    private static final Color[] defaultColors = {
        Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.PINK,
        Color.CYAN, Color.ORANGE, Color.WHITE, Color.MAGENTA, Color.GRAY };
    private static final Color[] defaultBorderColors = {
        new Color(0x770000), new Color(0x000077), new Color(0x007700), new Color(0x777700), new Color(0x773333),
        new Color(0x007777), new Color(0x993300), Color.GRAY, new Color(0x770077), Color.BLACK };
    private final String playerId;
    private String name;
    private Node node;
    private int hitpoints = 18;
    private int gear;
    private int curveStops;
    private double angle;
    private final Color color1;
    private final Color color2;
    private boolean stopped;
    private final JPanel panel; // for repaint requests needed for animations
    private final List<Node> route = new ArrayList<>();
    private static final Color transparentWhite = new Color(1.0f, 1.0f, 1.0f, 0.3f);
    private boolean modifyingHitpoints;
    private int lapsToGo;

    public Player(String playerId, Node node, double initialAngle, JPanel panel) {
        this.playerId = playerId;
        color1 = defaultBorderColors[colorIndex];
        color2 = defaultColors[colorIndex++];
        this.node = node;
        this.angle = initialAngle;
        this.panel = panel;
    }

    public String getId() {
        return playerId;
    }

    public String getNameAndId() {
        return name + " (" + playerId + ")";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void drawRoll(Graphics2D g2d, @Nullable Integer roll) {
        if (roll != null && gear != 0) {
            final Color color = getGearColor(gear);
            g2d.setColor(color);
            g2d.setFont(new Font("Arial", Font.PLAIN, 20));
            final int x = (roll >= 10) ? 30 : 36;
            g2d.drawString(Integer.toString(roll), x, 48);
        }
    }

    private Color getGearColor(int gear) {
        switch (gear) {
            case 0:
                return Color.BLACK;
            case 1:
                return Color.YELLOW;
            case 2:
                return Color.ORANGE;
            case 3:
                return Color.RED;
            case 4:
                return Color.GREEN;
            case 5:
                return Color.MAGENTA;
            case 6:
                return Color.BLUE;
            default:
                throw new RuntimeException("Invalid gear " + gear);
        }
    }

    public void highlight(Graphics2D g2d, Map<Node, Point> coordinates) {
        final Point p = coordinates.get(node);
        MapEditor.drawOval(g2d, p.x, p.y, 14, 14, true, false, Color.GREEN, 1);
    }

    public void draw(Graphics2D g2d, Map<Node, Point> coordinates) {
        if (!stopped) {
            if (route.size() > 1) {
                drawRoute(g2d, coordinates);
            }
            final Point p = coordinates.get(node);
            draw(g2d, p.x, p.y, angle);
        }
    }

    public void draw(Graphics2D g, int x, int y, double angle) {
        g.setColor(Color.BLACK);
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        g.transform(at);
        g.rotate(angle);
        g.fillRect(-7, -3, 1, 7);
        g.fillRect(-6, 0, 1, 1);
        g.fillRect(-4, -4, 3, 2);
        g.fillRect(-4, 3, 3, 2);
        g.fillRect(6, -3, 1, 7);
        g.fillRect(3, -2, 1, 1);
        g.fillRect(3, 2, 1, 1);
        g.fillRect(2, -4, 3, 2);
        g.fillRect(2, 3, 3, 2);
        g.setColor(color1);
        g.fillRect(-5, -2, 6, 5);
        g.fillRect(1, -1, 5, 3);
        g.setColor(color2);
        g.fillRect(-4, -1, 5, 3);
        g.fillRect(1, 0, 5, 1);
        g.setColor(color1);
        g.fillRect(-3, 0, 3, 1);
        // needed if something is drawn after this
        g.rotate(-angle);
        g.translate(-x, -y);
    }

    public void drawStats(Graphics2D g, int x, int y) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.drawString(name, x, y);
        if (stopped) {
            g.drawString(hitpoints > 0 ? "Finished" : "DNF", x + 110, y);
        } else {
            if (modifyingHitpoints) {
                g.setColor(Color.RED);
            }
            g.drawString("HP: " + Integer.toString(hitpoints), x + 110, y);
            g.setColor(Color.BLACK);
            g.setColor(getGearColor(gear));
            g.drawString("G: " + Integer.toString(gear), x + 160, y);
            g.setColor(Color.BLACK);
            g.drawString("S: " + Integer.toString(curveStops), x + 190, y);
        }
    }

    public void move(Node n, Map<Node, Point> coordinates) {
        if (route.isEmpty()) {
            route.add(node);
        }
        final Point p1 = coordinates.get(node);
        final Point p2 = coordinates.get(n);
        angle = Math.atan2(p2.y - p1.y, p2.x - p1.x);
        node = n;
        route.add(n);
        panel.repaint();
    }

    public void setGear(int gear) {
        modifyingHitpoints = false;
        if (gear != this.gear) {
            this.gear = gear;
            panel.repaint();
        }
    }

    public void setHitpoints(int hitpoints) {
        if (hitpoints != this.hitpoints) {
            this.hitpoints = hitpoints;
            modifyingHitpoints = true;
            if (hitpoints <= 0) {
                stopped = true;
            }
            panel.repaint();
        }
    }

    public void setLapsRemaining(int lapsToGo) {
        if (lapsToGo != this.lapsToGo) {
            this.lapsToGo = lapsToGo;
            if (lapsToGo < 0) {
                stopped = true;
            }
            panel.repaint();
        }
    }

    public void setCurveStops(int curveStops) {
        if (curveStops != this.curveStops) {
            this.curveStops = curveStops;
            panel.repaint();
        }
    }

    public void clearRoute() {
        route.clear();
    }

    private void drawRoute(Graphics2D g2d, Map<Node, Point> coordinates) {
        g2d.setColor(transparentWhite);
        for (int i = 0; i < route.size() - 1; i++) {
            final Point n1 = coordinates.get(route.get(i));
            final Point n2 = coordinates.get(route.get(i + 1));
            g2d.drawLine(n1.x, n1.y, n2.x, n2.y);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof Player) {
            return playerId.equals(((Player) other).getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return playerId.hashCode();
    }
}
