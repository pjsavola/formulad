package gp;

import gp.ai.Node;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

public class Player {
    final String playerId;
    private String name;
    Node node;
    int hitpoints = 18;
    int gear;
    int curveStops;
    private double angle;
    private final Color color1;
    private final Color color2;
    private Color[] colorVariants;
    boolean stopped;
    private final JPanel panel; // for repaint requests needed for animations
    private final List<Node> route = new ArrayList<>();
    private static final Color transparentWhite = new Color(1.0f, 1.0f, 1.0f, 0.3f);
    private int lapsToGo;
    static final Font rollFont = new Font("Arial", Font.PLAIN, 20);
    private static final Font statsFont = new Font("Arial", Font.PLAIN, 12);

    private static Color[] createColorVariants(Color color) {
        Color[] colors = new Color[9];
        colors[0] = manipulateColor(color, 0.7f);
        colors[1] = manipulateColor(color, 0.75f);
        colors[2] = manipulateColor(color, 0.8f);
        colors[3] = manipulateColor(color, 0.85f);
        colors[4] = manipulateColor(color, 0.9f);
        colors[5] = manipulateColor(color, 0.95f);
        colors[6] = manipulateColor(color, 1.f);
        colors[7] = manipulateColor(color, 1.1f);
        colors[8] = manipulateColor(color, 1.2f);
        return colors;
    }

    public Player(String playerId, Node node, double initialAngle, JPanel panel, int color1, int color2) {
        this.playerId = playerId;
        this.color1 = new Color(color1);
        this.color2 = new Color(color2);
        this.node = node;
        this.angle = initialAngle / 180 * Math.PI;
        this.panel = panel;
        this.colorVariants = createColorVariants(this.color1);
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

    public void drawRoll(Graphics2D g2d, @Nullable Integer roll, int circleX) {
        if (roll != null && gear != 0) {
            final Color color = getGearColor(gear);
            g2d.setColor(color);
            g2d.setFont(rollFont);
            final int x = circleX - (roll >= 10 ? 10 : 4);
            g2d.drawString(Integer.toString(roll), x, 48);
        }
    }

    Color getGearColor(int gear) {
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

    public void highlight(Graphics2D g2d) {
        final Point p = node.getLocation();
        MapEditor.drawOval(g2d, p.x, p.y, 20, 20, true, false, Color.GREEN, 1);
    }

    public void draw(Graphics2D g2d) {
        if (!stopped) {
            synchronized (route) {
                if (route.size() > 1) {
                    drawRoute(g2d);
                }
            }
            final Point p = node.getLocation();
            draw(g2d, p.x, p.y, angle);
        } else if (hitpoints <= 0) {
            // Draw small x for retired players
            final Point p = node.getLocation();
            g2d.setColor(color1);
            g2d.drawLine(p.x - 2, p.y - 2, p.x + 2, p.y + 2);
            g2d.setColor(color2);
            g2d.drawLine(p.x + 2, p.y - 2, p.x - 2, p.y + 2);
            g2d.setColor(Color.BLACK);
            g2d.drawLine(p.x, p.y, p.x, p.y);
        }
    }

    public void drawRetired(Graphics2D g2d) {
        if (stopped && hitpoints <= 0) {
            // Draw small x for retired players
            final Point p = node.getLocation();
            g2d.setColor(color1);
            g2d.drawLine(p.x - 2, p.y - 2, p.x + 2, p.y + 2);
            g2d.drawLine(p.x + 2, p.y - 2, p.x - 2, p.y + 2);
        }
    }

    public void draw(Graphics2D g, int x, int y, double angle) {
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        g.transform(at);
        g.rotate(angle);
        draw(g, colorVariants, color2);
        g.rotate(-angle);
        g.translate(-x, -y);
    }

    public static Color manipulateColor(Color color, float factor) {
        int r = Math.max(0, Math.min(255, Math.round(color.getRed() * factor)));
        int g = Math.max(0, Math.min(255, Math.round(color.getGreen() * factor)));
        int b = Math.max(0, Math.min(255, Math.round(color.getBlue() * factor)));
        return new Color(r, g, b);
    }

    static void draw(Graphics2D g, Color[] colors, Color color2) {
        g.setColor(colors[0]);
        g.fillRect(8, 0, 1, 1);
        g.setColor(colors[1]);
        g.fillRect(7, 0, 1, 1);
        g.setColor(colors[2]);
        g.fillRect(6, 0, 1, 1);
        g.setColor(colors[3]);
        g.fillRect(5, -1, 1, 3);
        g.setColor(colors[4]);
        g.fillRect(4, -1, 1, 3);
        g.setColor(colors[5]);
        g.fillRect(3, -1, 1, 3);
        g.fillRect(0, -1, 1, 3);
        g.fillRect(-1, -1, 1, 3);
        g.fillRect(-4, -1, 1, 3);
        g.setColor(colors[7]);
        g.fillRect(1, -1, 1, 3);
        g.fillRect(-3, -1, 1, 3);
        g.setColor(colors[8]);
        g.fillRect(-2, -1, 1, 3);
        g.setColor(colors[6]);
        g.fillRect(2, -1, 1, 3);
        g.fillRect(-4, -2, 6, 1);
        g.fillRect(-4, 2, 6, 1);
        g.fillRect(-3, -3, 3, 1);
        g.fillRect(-3, 3, 3, 1);
        g.setColor(color2);
        g.fillRect(7, 3, 2, 1);
        g.fillRect(7, -3, 2, 1);
        g.fillRect(-7, -2, 3, 5);
        g.setColor(color2.brighter());
        g.fillRect(-6, -1, 2, 3);
        g.fillRect(7, 2, 2, 1);
        g.fillRect(7, -2, 2, 1);
        g.fillRect(8, 1, 1, 1);
        g.fillRect(8, -1, 1, 1);
        g.setColor(Color.BLACK);
        g.fillRect(3, -4, 2, 2);
        g.fillRect(3, 3, 2, 2);
        g.fillRect(-6, -4, 2, 2);
        g.fillRect(-6, 3, 2, 2);
        g.fillRect(4, -2, 1, 1);
        g.fillRect(4, 2, 1, 1);
        g.fillRect(-1, 0, 2, 1);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(5, -4, 1, 2);
        g.fillRect(5, 3, 1, 2);
        g.fillRect(-4, -4, 1, 2);
        g.fillRect(-4, 3, 1, 2);
    }

    public static void draw(Graphics2D g, int x, int y, double angle, Color color1, Color color2, double scale) {
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        at.scale(scale, scale);
        g.transform(at);
        g.rotate(angle);
        if (true) {
            Color[] colors = createColorVariants(color1);
            draw(g, colors, color2);
        }
        else if (true) {
            g.setColor(color1);
            g.fillRect(6, -3, 2, 1);
            g.fillRect(6, 3, 2, 1);
            g.fillRect(5, 0, 3, 1);
            g.fillRect(1, -1, 4, 3);
            g.fillRect(-7, -2, 9, 5);
            g.setColor(color2);
            g.fillRect(6, -2, 2, 1);
            g.fillRect(6, 2, 2, 1);
            g.fillRect(7, -1, 1, 1);
            g.fillRect(7, 1, 1, 1);
            g.fillRect(-4, -3, 5, 2);
            g.fillRect(-4, 2, 5, 2);
            g.fillRect(-4, 0, 4, 1);
            g.fillRect(-6, -1, 2, 3);
            g.setColor(Color.BLACK);
            g.fillRect(2, -4, 3, 2);
            g.fillRect(2, 3, 3, 2);
            g.fillRect(-6, -4, 3, 2);
            g.fillRect(-6, 3, 3, 2);
            g.fillRect(3, -2, 1, 1);
            g.fillRect(3, 2, 1, 1);
            g.fillRect(-1, 0, 2, 1);
        } else {
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
        }
        // needed if something is drawn after this
        g.rotate(-angle);
        g.translate(-x, -y);
    }

    public void drawStats(Graphics2D g, int x, int y, Map<String, Integer> hitpointMap) {
        g.setColor(Color.BLACK);
        g.setFont(statsFont);
        g.drawString(name, x, y);
        if (stopped) {
            g.drawString(hitpoints > 0 ? "Finished" : "DNF", x + 110, y);
        } else {
            final Integer savedHitpooints = hitpointMap.get(playerId);
            if (savedHitpooints != null && savedHitpooints != hitpoints) {
                g.setColor(savedHitpooints > hitpoints ? Color.RED : Color.GREEN);
            }
            g.drawString("HP: " + Integer.toString(hitpoints), x + 110, y);
            g.setColor(Color.BLACK);
            g.setColor(getGearColor(gear));
            g.drawString("G: " + Integer.toString(gear), x + 160, y);
            g.setColor(Color.BLACK);
            g.drawString("S: " + Integer.toString(curveStops), x + 190, y);
        }
    }

    public void move(Node n) {
        synchronized (route) {
            if (route.isEmpty()) {
                route.add(node);
            }
            final Point p1 = node.getLocation();
            final Point p2 = n.getLocation();
            angle = Math.atan2(p2.y - p1.y, p2.x - p1.x);
            node = n;
            route.add(n);
            panel.repaint();
        }
    }

    public void setGear(int gear) {
        if (gear != this.gear) {
            this.gear = gear;
        }
    }

    public void setHitpoints(int hitpoints) {
        if (hitpoints != this.hitpoints) {
            this.hitpoints = hitpoints;
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
        synchronized (route) {
            route.clear();
        }
    }

    private void drawRoute(Graphics2D g2d) {
        g2d.setColor(transparentWhite);
        for (int i = 0; i < route.size() - 1; i++) {
            final Point n1 = route.get(i).getLocation();
            final Point n2 = route.get(i + 1).getLocation();
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
