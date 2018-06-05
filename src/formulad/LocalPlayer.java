package formulad;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.sun.istack.internal.Nullable;

public class LocalPlayer {
    private static int colorIndex = 0;
    private static final Color[] defaultColors = {
        Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.PINK,
        Color.CYAN, Color.ORANGE, Color.WHITE, Color.MAGENTA, Color.GRAY };
    private static final Color[] defaultBorderColors = {
        new Color(0x770000), new Color(0x000077), new Color(0x007700), new Color(0x777700), new Color(0x773333),
        new Color(0x007777), new Color(0x993300), Color.GRAY, new Color(0x770077), Color.BLACK };
    private final int playerId;
    private final String name;
    private Node node;
    private int hitpoints = 18;
    private int gear;
    private int curveStops;
    private double angle;
    private List<Node> route;
    public int lapsToGo;
    private final Color color1;
    private final Color color2;
    private boolean stopped;
    private final List<List<Node>> paths = new ArrayList<>();

    public LocalPlayer(int playerId, String name, Node node, double initialAngle, int laps) {
        this.playerId = playerId;
        this.name = name;
        color1 = defaultBorderColors[colorIndex];
        color2 = defaultColors[colorIndex++];
        this.node = node;
        this.angle = initialAngle;
        lapsToGo = laps;
    }

    public int getId() {
        return playerId;
    }

    // 1. lowest number of laps to go
    // 2. covered distance of current lap
    // 3. higher gear
    // 4. inside line in curve
    public int compareTo(LocalPlayer player, Map<Node, Double> distanceMap) {
        if (lapsToGo == player.lapsToGo) {
            final double d1 = distanceMap.get(node);
            final double d2 = distanceMap.get(player.node);
            if (d1 < d2) return 1;
            else if (d2 < d1) return -1;
            if (gear == player.gear) {
                if (node.isCurve() && !player.node.isCurve()) return 1;
                else if (!node.isCurve() && player.node.isCurve()) return -1;
                return player.node.getDistanceToNextArea(!player.node.isCurve()) - node.getDistanceToNextArea(!node.isCurve());
            }
            return player.gear - gear;
        }
        return lapsToGo - player.lapsToGo;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void stop() {
        if (stopped) {
            throw new RuntimeException(name + " is stopped twice!");
        }
        if (lapsToGo < 0) {
            System.err.println(name + " finished the race!");
        } else {
            System.err.println(name + " dropped from the race!");
        }
        stopped = true;
        route = null;
    }

    public void drawStats(Graphics2D g2d, @Nullable Integer roll) {
        // TODO: Clean up
        if (roll != null) {
            g2d.setColor(Color.GREEN);
            final int width = roll >= 10 ? 25 : 20;
            final int x = (roll >= 10) ? 43 : 46;
            MapEditor.drawOval(g2d, 50, 20, width, 20, true, true, Color.BLACK, 1);
            g2d.drawString(Integer.toString(roll), x, 24);
        }
        g2d.setColor(Color.RED);
        final int width = hitpoints >= 10 ? 25 : 20;
        final int x = hitpoints >= 10 ? 13 : 16;
        MapEditor.drawOval(g2d, 20, 50, width, 20, true, true, Color.BLACK, 1);
        g2d.drawString(Integer.toString(hitpoints), x, 54);
        MapEditor.drawOval(g2d, 20, 20, 20, 20, true, true, Color.BLACK, 1);
        g2d.setColor(Color.WHITE);
        g2d.drawString(Integer.toString(gear), 16, 24);
    }

    public void highlight(final Graphics2D g2d) {
        MapEditor.drawOval(g2d, node.x, node.y, 12, 12, true, false, Color.GREEN, 1);
    }

    public void drawPath(final Graphics g) {
        if (route != null) {
            for (int i = 0; i < route.size() - 1; i++) {
                final Node n1 = route.get(i);
                final Node n2 = route.get(i + 1);
                g.drawLine(n1.x, n1.y, n2.x, n2.y);
            }
        }
    }

    public void draw(final Graphics2D g) {
        draw(g, node.x, node.y, angle);
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
        if (stopped) {
            if (lapsToGo < 0) {
                g.setColor(Color.GREEN);
            } else {
                g.setColor(Color.BLACK);
            }
            g.drawLine(-7, -4, 6, 4);
            g.drawLine(-7, 4, 6, -4);
        }
        // needed if something is drawn after this
        g.rotate(-angle);
        g.translate(-x, -y);
    }

    public void drawStats(Graphics2D g, int x, int y) {
        g.setColor(Color.BLACK);
        g.drawString(name + " HP: " + Integer.toString(hitpoints) + " G: " + Integer.toString(gear) + " S: " + Integer.toString(curveStops), x, y);
    }

    public boolean switchGear(final int newGear) {
        if (newGear < 1 || newGear > 6) return false;

        if (Math.abs(newGear - gear) <= 1) {
            gear = newGear;
            return true;
        }

        // downwards more than 1
        final int damage = gear - newGear - 1;
        if (damage > 0 && damage < 4 && hitpoints > damage) {
            adjustHitpoints(damage);
            gear = newGear;
            return true;
        }
        return false;
    }

    // TODO: Is the distribution equal?
    public int roll() {
        switch (gear) {
            case 1: return 2;
            case 2: return 4;
            case 3: return 8;
            case 4: return 12;
            case 5: return 20;
            case 6: return 30;
        }
        switch (gear) {
            case 1: return Game.r.nextInt(2) + 1; // d4
            case 2: return Game.r.nextInt(3) + 2; // d6
            case 3: return Game.r.nextInt(5) + 4; // d8
            case 4: return Game.r.nextInt(6) + 7; // d12
            case 5: return Game.r.nextInt(10) + 11; // d20
            case 6: return Game.r.nextInt(10) + 21; // d20
        }
        throw new RuntimeException("Invalid gear: " + gear);
    }

    public void move(int[] target, int pathIndex) {
        move(new DamageAndPath(target[1] + target[2], paths.get(pathIndex)));
    }

    private void move(DamageAndPath dp) {
        final List<Node> route = dp.getPath();
        if (route == null || route.isEmpty()) {
            throw new RuntimeException("Invalid route: " + route);
        }
        if (node != null && route.get(0) != node) {
            throw new RuntimeException("Invalid starting point for route: " + route);
        }
        int size = route.size();
        if (size > 1) {
            final Node n1 = route.get(size - 2);
            final Node n2 = route.get(size - 1);
            angle = Math.atan2(n2.y - n1.y, n2.x - n1.x);
        }
        if (node != null && node.type != MapEditor.FINISH) {
            for (final Node node : route) {
                if (node.type == MapEditor.FINISH) {
                    lapsToGo--;
                    break;
                }
            }
        }
        this.route = route;
        node = route.get(size - 1);
        boolean onlyCurves = true;
        for (final Node node : route) {
            if (!node.isCurve()) {
                onlyCurves = false;
                break;
            }
        }
        if (!onlyCurves) {
            // path contained non-curve nodes
            curveStops = 0;
        }
        if (node.isCurve()) {
            // movement ended in a curve
            curveStops++;
        }
        if (dp.getDamage() != 0) {
            adjustHitpoints(dp.getDamage());
        }
        if (hitpoints <= 0) {
            throw new RuntimeException("Illegal move: Too much damage");
        }
        if (lapsToGo < 0) {
            stop();
        }
    }

    private void adjustHitpoints(int loss) {
        System.err.println(name + " loses " + loss + " hitpoints");
        hitpoints -= loss;
    }

    public int[][] findAllTargets(int roll, List<LocalPlayer> players) {
        int braking = 0;
        paths.clear();
        final List<int[]> result = new ArrayList<>();
        final Set<Node> forbiddenNodes = players
            .stream()
            .map(player -> player.node)
            .collect(Collectors.toSet());
        while (braking < hitpoints) {
            final Map<Node, DamageAndPath> targets = findTargetNodes(roll - braking, forbiddenNodes);
            for (Map.Entry<Node, DamageAndPath> e : targets.entrySet()) {
                if (e.getValue().getDamage() + braking < hitpoints) {
                    result.add(new int[]{e.getKey().id, e.getValue().getDamage(), braking});
                    paths.add(e.getValue().getPath());
                }
            }
            if (braking == roll) {
                break;
            }
            braking++;
        }
        final int[][] targets = new int[result.size()][];
        for (int i = 0; i < result.size(); i++) {
            targets[i] = result.get(i);
        }
        return targets;
    }

    private Map<Node, DamageAndPath> findTargetNodes(final int roll, final Set<Node> forbiddenNodes) {
        final Map<Node, DamageAndPath> result = NodeUtil.findNodes(node, roll, forbiddenNodes, true, curveStops, lapsToGo == 0);
        final Map<Node, DamageAndPath> targets = new HashMap<>();
        for (final Map.Entry<Node, DamageAndPath> entry : result.entrySet()) {
            if (entry.getValue().getDamage() < hitpoints) {
                targets.put(entry.getKey(), entry.getValue());
            }
        }
        return targets;
    }

    public static void possiblyAddEngineDamage(final List<LocalPlayer> players) {
        for (final LocalPlayer player : players) {
            if (player.gear == 5 || player.gear == 6) {
                if (Game.r.nextInt(20) < 4) {
                    player.adjustHitpoints(1);
                    if (player.hitpoints <= 0) {
                        player.stop();
                    }
                }
            }
        }
    }

    public void collide(final List<LocalPlayer> players, Map<Node, List<Node>> prevNodeMap) {
        for (final LocalPlayer player : players) {
            if (player != this && node.isCloseTo(player.node, prevNodeMap)) {
                System.err.println(name + " almost collides with " + player.name);
                if (Game.r.nextInt(20) < 4) {
                    adjustHitpoints(1);
                    if (hitpoints <= 0) {
                        stop();
                    }
                }
                if (Game.r.nextInt(20) < 4) {
                    player.adjustHitpoints(1);
                    if (player.hitpoints <= 0) {
                        player.stop();
                    }
                }
            }
        }
    }

    public int[] getData() {
        return new int[] { playerId, node.id, hitpoints, gear, curveStops };
    }
}
