package gp;

import gp.ai.Node;
import gp.model.Tires;

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
    private List<List<Color>> colorVariants;
    boolean stopped;
    private final JPanel panel; // for repaint requests needed for animations
    private final List<Node> route = new ArrayList<>();
    private static final Color transparentWhite = new Color(1.0f, 1.0f, 1.0f, 0.3f);
    int lapsToGo;
    Tires tires;
    static final Font rollFont = new Font("Arial", Font.PLAIN, 20);
    private static final Font statsFont = new Font("Arial", Font.PLAIN, 12);

    private static List<List<Color>> createColorVariants(int[] colors) {
        final List<List<Color>> variants = new ArrayList<>();
        variants.add(new ArrayList<>());
        variants.add(new ArrayList<>());
        variants.add(new ArrayList<>());
        variants.add(new ArrayList<>());
        Color color = new Color(colors[0]);
        variants.get(0).add(manipulateColor(color, 0.7f));
        variants.get(0).add(manipulateColor(color, 0.75f));
        variants.get(0).add(manipulateColor(color, 0.8f));
        variants.get(0).add(manipulateColor(color, 0.85f));
        variants.get(0).add(manipulateColor(color, 0.9f));
        variants.get(0).add(manipulateColor(color, 0.95f));
        variants.get(0).add(manipulateColor(color, 1.f));
        color = new Color(colors[1]);
        variants.get(1).add(color);
        variants.get(1).add(color.brighter());
        color = new Color(colors[2]);
        variants.get(2).add(manipulateColor(color, 0.95f));
        variants.get(2).add(manipulateColor(color, 1.f));
        variants.get(2).add(manipulateColor(color, 1.1f));
        variants.get(2).add(manipulateColor(color, 1.2f));
        color = new Color(colors[3]);
        variants.get(3).add(color);
        return variants;
    }

    public Player(String playerId, Node node, double initialAngle, JPanel panel, int[] colors) {
        this.playerId = playerId;
        this.node = node;
        this.angle = initialAngle / 180 * Math.PI;
        this.panel = panel;
        this.colorVariants = createColorVariants(colors);
    }

    public String getId() {
        return playerId;
    }

    String getNameAndId() {
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

    public void setTires(Tires tires) {
        this.tires = tires;
    }

    void drawRoll(Graphics2D g2d, @Nullable Integer roll, Point point) {
        if (roll != null && gear != 0) {
            final Color color = getGearColor(gear);
            g2d.setColor(color);
            g2d.setFont(rollFont);
            final int x = point.x - (roll >= 10 ? 10 : 4);
            g2d.drawString(Integer.toString(roll), x, point.y + 8);
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

    void highlight(Graphics2D g2d) {
        final Point p = node.getLocation();
        MapEditor.drawOval(g2d, p.x, p.y, 20, 20, false, Color.GREEN, 1);
    }

    void draw(Graphics2D g2d) {
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
            g2d.setColor(colorVariants.get(0).get(6));
            g2d.drawLine(p.x - 2, p.y - 2, p.x + 2, p.y + 2);
            g2d.setColor(colorVariants.get(1).get(1));
            g2d.drawLine(p.x + 2, p.y - 2, p.x - 2, p.y + 2);
            g2d.setColor(Color.BLACK);
            g2d.drawLine(p.x, p.y, p.x, p.y);
        }
    }

    void drawRetired(Graphics2D g2d) {
        if (stopped && hitpoints <= 0) {
            // Draw small x for retired players
            final Point p = node.getLocation();
            g2d.setColor(colorVariants.get(0).get(6));
            g2d.drawLine(p.x - 2, p.y - 2, p.x + 2, p.y + 2);
            g2d.drawLine(p.x + 2, p.y - 2, p.x - 2, p.y + 2);
        }
    }

    void draw(Graphics2D g, int x, int y, double angle) {
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        g.transform(at);
        g.rotate(angle);
        draw(g, colorVariants, tires);
        g.rotate(-angle);
        g.translate(-x, -y);
    }

    private static Color manipulateColor(Color color, float factor) {
        int r = Math.max(0, Math.min(255, Math.round(color.getRed() * factor)));
        int g = Math.max(0, Math.min(255, Math.round(color.getGreen() * factor)));
        int b = Math.max(0, Math.min(255, Math.round(color.getBlue() * factor)));
        return new Color(r, g, b);
    }

    private static void draw(Graphics2D g, List<List<Color>> colors, Tires tires) {
        g.setColor(colors.get(0).get(0));
        g.fillRect(8, 0, 1, 1);
        g.setColor(colors.get(0).get(1));
        g.fillRect(7, 0, 1, 1);
        g.setColor(colors.get(0).get(2));
        g.fillRect(6, 0, 1, 1);
        g.setColor(colors.get(0).get(3));
        g.fillRect(5, -1, 1, 3);
        g.setColor(colors.get(0).get(4));
        g.fillRect(4, -1, 1, 3);
        g.setColor(colors.get(0).get(5));
        g.fillRect(3, -1, 1, 3);
        g.setColor(colors.get(0).get(6));
        g.fillRect(2, -1, 1, 3);
        g.fillRect(1, -1, 1, 3);
        g.fillRect(-4, -2, 6, 1);
        g.fillRect(-4, 2, 6, 1);
        g.fillRect(-3, -3, 3, 1);
        g.fillRect(-3, 3, 3, 1);
        g.setColor(colors.get(2).get(0));
        g.fillRect(2, 0, 1, 1);
        g.fillRect(0, -1, 1, 3);
        g.setColor(colors.get(2).get(1));
        g.fillRect(-1, -1, 1, 3);
        g.fillRect(-4, -1, 1, 3);
        g.setColor(colors.get(2).get(2));
        g.fillRect(1, 0, 1, 1);
        g.fillRect(-3, -1, 1, 3);
        g.setColor(colors.get(2).get(3));
        g.fillRect(-2, -1, 1, 3);
        g.setColor(colors.get(1).get(0));
        g.fillRect(7, 3, 2, 1);
        g.fillRect(7, -3, 2, 1);
        g.fillRect(-7, -2, 3, 5);
        g.setColor(colors.get(1).get(1));
        g.fillRect(-6, -1, 2, 3);
        g.fillRect(7, 2, 2, 1);
        g.fillRect(7, -2, 2, 1);
        g.fillRect(8, 1, 1, 1);
        g.fillRect(8, -1, 1, 1);
        g.setColor(Color.BLACK);
        g.fillRect(4, -2, 1, 1);
        g.fillRect(4, 2, 1, 1);
        g.fillRect(-1, 0, 2, 1);
        Color tireColor = null;
        if (tires != null && tires.getType() == Tires.Type.SOFT) {
            tireColor = tires.canUse() ? new Color(0x8B008B) : new Color(0x8B0000);
            tireColor = tireColor.brighter();
            g.setColor(tireColor);
        }
        g.fillRect(3, -4, 2, 2);
        g.fillRect(3, 3, 2, 2);
        g.fillRect(-6, -4, 2, 2);
        g.fillRect(-6, 3, 2, 2);
        g.setColor(colors.get(3).get(0));
        g.fillRect(-1, 0, 1, 1);
        g.setColor(tireColor == null ? Color.DARK_GRAY : tireColor.brighter());
        g.fillRect(5, -4, 1, 2);
        g.fillRect(5, 3, 1, 2);
        g.fillRect(-4, -4, 1, 2);
        g.fillRect(-4, 3, 1, 2);
    }

    static void draw(Graphics2D g, int x, int y, double angle, int[] colorCodes, double scale) {
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        at.scale(scale, scale);
        g.transform(at);
        g.rotate(angle);
        final Color color1 = new Color(colorCodes[0]);
        final Color color2 = new Color(colorCodes[1]);
        List<List<Color>> colors = createColorVariants(colorCodes);
        draw(g, colors, null);
        // needed if something is drawn after this
        g.rotate(-angle);
        g.translate(-x, -y);
    }

    void drawStats(Graphics2D g, int x, int y, Map<String, Integer> hitpointMap) {
        g.setColor(Color.BLACK);
        g.setFont(statsFont);
        g.drawString(name, x, y);
        if (stopped) {
            g.drawString(hitpoints > 0 ? "Finished" : "DNF", x + UIUtil.infoBoxWidth - 170, y);
        } else {
            final Integer savedHitpooints = hitpointMap.get(playerId);
            if (savedHitpooints != null && savedHitpooints != hitpoints) {
                g.setColor(savedHitpooints > hitpoints ? Color.RED : Color.GREEN);
            }
            g.drawString("HP: " + Integer.toString(hitpoints), x + UIUtil.infoBoxWidth - 170, y);
            g.setColor(Color.BLACK);
            g.setColor(getGearColor(gear));
            g.drawString("G: " + Integer.toString(gear), x + UIUtil.infoBoxWidth - 120, y);
            g.setColor(Color.BLACK);
            g.drawString("S: " + Integer.toString(curveStops), x + UIUtil.infoBoxWidth - 90, y);
            g.drawString("L: " + Integer.toString(lapsToGo + 1), x + UIUtil.infoBoxWidth - 60, y);
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

    void setLapsRemaining(int lapsToGo) {
        if (lapsToGo != this.lapsToGo) {
            this.lapsToGo = lapsToGo;
            if (lapsToGo < 0) {
                stopped = true;
            }
            panel.repaint();
        }
    }

    void setCurveStops(int curveStops) {
        if (curveStops != this.curveStops) {
            this.curveStops = curveStops;
            panel.repaint();
        }
    }

    void clearRoute() {
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
