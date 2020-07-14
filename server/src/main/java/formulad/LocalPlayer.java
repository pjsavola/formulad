package formulad;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.swing.JPanel;

import formulad.ai.Gear;
import formulad.ai.Node;

import formulad.model.*;

public final class LocalPlayer {
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
    private int lapsToGo;
    private final Color color1;
    private final Color color2;
    private boolean stopped;
    private final List<DamageAndPath> paths = new ArrayList<>();
    private final JPanel panel; // for repaint requests needed for animations
    private boolean modifyingHitpoints;
    private boolean modifyingGear;
    public static int animationDelayInMillis;
    private long timeUsed;
    private int exceptions;
    private int turns;
    private final List<Node> route = new ArrayList<>();
    private static final Color transparentWhite = new Color(1.0f, 1.0f, 1.0f, 0.3f);
    private int leeway;
    private int gridPosition;

    public LocalPlayer(String playerId, Node node, double initialAngle, int laps, JPanel panel, int leeway) {
        this.playerId = playerId;
        color1 = defaultBorderColors[colorIndex];
        color2 = defaultColors[colorIndex++];
        this.node = node;
        this.angle = initialAngle;
        lapsToGo = laps;
        this.panel = panel;
        this.leeway = leeway;
    }

    public String getId() {
        return playerId;
    }

    public String getNameAndId() {
        return name + " (" + playerId + ")";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getGridPosition() {
        return gridPosition;
    }

    public void setGridPosition(int gridPosition) {
        this.gridPosition = gridPosition;
    }

    // 1. lowest number of laps to go
    // 2. covered distance of current lap
    // 3. higher gear
    // 4. inside line in curve
    // 5. order of arrival
    public int compareTo(LocalPlayer player, Map<Node, Double> distanceMap, List<LocalPlayer> stoppedPlayers) {
        if (lapsToGo == player.lapsToGo) {
            if (lapsToGo < 0) {
                final int index1 = stoppedPlayers.indexOf(this);
                final int index2 = stoppedPlayers.indexOf(player);
                return index1 > index2 ? 1 : -1;
            }
            final double d1 = distanceMap.get(node);
            final double d2 = distanceMap.get(player.node);
            if (d1 < d2) return 1;
            else if (d2 < d1) return -1;
            if (gear == player.gear) {
                if (node.isCurve() && !player.node.isCurve()) return 1;
                else if (!node.isCurve() && player.node.isCurve()) return -1;
                final int distanceToNextArea1 = node.getDistanceToNextArea();
                final int distanceToNextArea2 = player.node.getDistanceToNextArea();
                if (distanceToNextArea1 == distanceToNextArea2) {
                    final int index1 = stoppedPlayers.indexOf(this);
                    final int index2 = stoppedPlayers.indexOf(player);
                    // 1-2 players must be stopped, because otherwise they wouldn't end up in same place.
                    if (index1 == -1) return 1;
                    if (index2 == -1) return -1;
                    return index1 > index2 ? 1 : -1;
                }
                return distanceToNextArea2 - distanceToNextArea1;
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
            FormulaD.log.info(getNameAndId() + " finished the race!");
        } else {
            if (hitpoints > 0) {
                adjustHitpoints(hitpoints);
            }
            FormulaD.log.info(getNameAndId() + " dropped from the race!");
        }
        gear = 0;
        stopped = true;
    }

    public void drawRoll(Graphics2D g2d, @Nullable Integer roll) {
        if (roll != null && gear != 0) {
            final Color color;
            switch (gear) {
                case 1:
                    color = Color.YELLOW;
                    break;
                case 2:
                    color = Color.ORANGE;
                    break;
                case 3:
                    color = Color.RED;
                    break;
                case 4:
                    color = Color.GREEN;
                    break;
                case 5:
                    color = Color.MAGENTA;
                    break;
                case 6:
                    color = Color.BLUE;
                    break;
                default:
                    throw new RuntimeException("Invalid gear " + gear);
            }
            g2d.setColor(color);
            g2d.setFont(new Font("Arial", Font.PLAIN, 20));
            final int x = (roll >= 10) ? 30 : 36;
            g2d.drawString(Integer.toString(roll), x, 48);
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
            g.drawString(lapsToGo < 0 ? "Finished" : "DNF", x + 110, y);
        } else {
            if (modifyingHitpoints) {
                g.setColor(Color.RED);
            }
            g.drawString("HP: " + Integer.toString(hitpoints), x + 110, y);
            g.setColor(Color.BLACK);
            if (modifyingGear) {
                g.setColor(Color.BLUE);
            }
            g.drawString("G: " + Integer.toString(gear), x + 160, y);
            g.setColor(Color.BLACK);
            g.drawString("S: " + Integer.toString(curveStops), x + 190, y);
        }
    }

    public boolean switchGear(int newGear) {
        if (newGear < 1 || newGear > 6) return false;

        if (newGear == gear) return true;

        if (Math.abs(newGear - gear) <= 1) {
            modifyingGear = true;
            gear = newGear;
            panel.repaint();
            try {
                Thread.sleep(animationDelayInMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            modifyingGear = false;
            return true;
        }

        // downwards more than 1
        final int damage = gear - newGear - 1;
        if (damage > 0 && damage < 4 && hitpoints > damage) {
            adjustHitpoints(damage);
            modifyingGear = true;
            while (gear > newGear) {
                gear--;
                panel.repaint();
                try {
                    Thread.sleep(animationDelayInMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            modifyingGear = false;
            return true;
        }
        return false;
    }

    public int roll(Random rng) {
        if (gear == 0) return 0;
        final int[] distribution = Gear.getDistribution(gear);
        final int roll = distribution[rng.nextInt(distribution.length)];
        ((FormulaD) panel).lobby.notifyClients(new RollNotification(playerId, gear, roll));
        return roll;
    }

    public void move(int index, Map<Node, Point> coordinates) {
        move(paths.get(index), coordinates);
    }

    private void move(DamageAndPath dp, Map<Node, Point> coordinates) {
        final List<Node> route = dp.getPath();
        if (route == null || route.isEmpty()) {
            throw new RuntimeException("Invalid route: " + route);
        }
        if (node != null && route.get(0) != node) {
            throw new RuntimeException("Invalid starting point for route: " + route);
        }
        final int oldLapsToGo = lapsToGo;
        int size = route.size();
        if (node != null && node.getType() != Node.Type.FINISH) {
            for (Node node : route) {
                if (node.getType() == Node.Type.FINISH) {
                    lapsToGo--;
                    break;
                }
            }
        }
        // Animation of the movement
        this.route.clear();
        if (size > 0) {
            this.route.add(route.get(0));
        }
        for (int i = 0; i < size - 1; i++) {
            final Node n1 = route.get(i);
            final Node n2 = route.get(i + 1);
            final Point p1 = coordinates.get(n1);
            final Point p2 = coordinates.get(n2);
            angle = Math.atan2(p2.y - p1.y, p2.x - p1.x);
            this.route.add(n2);
            ((FormulaD) panel).lobby.notifyClients(new MovementNotification(playerId, n2.getId()));
            node = n2;
            panel.repaint();
            try {
                Thread.sleep(animationDelayInMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        boolean onlyCurves = true;
        for (Node node : route) {
            if (!node.isCurve()) {
                onlyCurves = false;
                break;
            }
        }
        final int oldCurveStops = curveStops;
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
            FormulaD.log.log(Level.SEVERE, getNameAndId() + " performed an illegal move, taking too much damage");
            stop();
        }
        if (lapsToGo < 0) {
            stop();
        }
        if (curveStops != oldCurveStops) {
            ((FormulaD) panel).lobby.notifyClients(new CurveStopNotification(playerId, curveStops));
        }
        if (lapsToGo != oldLapsToGo) {
            ((FormulaD) panel).lobby.notifyClients(new LapChangeNotification(playerId, lapsToGo));
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

    private void adjustHitpoints(int loss) {
        FormulaD.log.info("Player " + getNameAndId() + " loses " + loss + " hitpoints");
        modifyingHitpoints = true;
        // Show animation
        int counter = loss;
        while (counter-- > 0) {
            hitpoints--;
            ((FormulaD) panel).lobby.notifyClients(new HitpointNotification(playerId, hitpoints));
            panel.repaint();
            try {
                Thread.sleep(animationDelayInMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        modifyingHitpoints = false;
    }

    public Moves findAllTargets(int roll, String gameId, List<LocalPlayer> players) {
        int braking = 0;
        final Set<Node> forbiddenNodes = players
            .stream()
            .map(player -> player.node)
            .collect(Collectors.toSet());
        paths.clear();
        final List<ValidMove> validMoves = new ArrayList<>();
        while (braking < hitpoints) {
            final Map<Node, DamageAndPath> targets = findTargetNodes(roll - braking, forbiddenNodes);
            for (Map.Entry<Node, DamageAndPath> e : targets.entrySet()) {
                final int damage = e.getValue().getDamage() + braking;
                if (damage < hitpoints) {
                    validMoves.add(new ValidMove()
                        .nodeId(e.getKey().getId())
                        .type(TypeEnum.valueOf(e.getKey().getType().name()))
                        .overshoot(e.getValue().getDamage())
                        .braking(braking)
                    );
                    paths.add(new DamageAndPath(damage, e.getValue().getPath()));
                }
            }
            if (braking == roll) {
                break;
            }
            braking++;
        }
        return new Moves().game(new GameId().gameId(gameId)).moves(validMoves);
    }

    private Map<Node, DamageAndPath> findTargetNodes(int roll, Set<Node> forbiddenNodes) {
        final Map<Node, DamageAndPath> result = NodeUtil.findNodes(node, roll, forbiddenNodes, true, curveStops, lapsToGo == 0);
        final Map<Node, DamageAndPath> targets = new HashMap<>();
        for (Map.Entry<Node, DamageAndPath> entry : result.entrySet()) {
            if (entry.getValue().getDamage() < hitpoints) {
                targets.put(entry.getKey(), entry.getValue());
            }
        }
        return targets;
    }

    public static void possiblyAddEngineDamage(List<LocalPlayer> players, Random rng) {
        FormulaD.log.info("20 or 30 was rolled, possibly adding engine damage for all players on gear 5 or 6");
        for (LocalPlayer player : players) {
            if (player.gear == 5 || player.gear == 6) {
                if (rng.nextInt(20) < 4) {
                    player.adjustHitpoints(1);
                    if (player.hitpoints <= 0) {
                        player.stop();
                    }
                }
            }
        }
    }

    public void collide(List<LocalPlayer> players, Map<Node, List<Node>> prevNodeMap, Random rng) {
        for (LocalPlayer player : players) {
            if (player.isStopped()) {
                continue;
            }
            if (player != this && node.isCloseTo(player.node, prevNodeMap)) {
                FormulaD.log.info(getNameAndId() + " is close to " + player.getNameAndId() + " and may collide");
                if (rng.nextInt(20) < 4) {
                    adjustHitpoints(1);
                    if (hitpoints <= 0) {
                        stop();
                    }
                }
                if (rng.nextInt(20) < 4) {
                    player.adjustHitpoints(1);
                    if (player.hitpoints <= 0) {
                        player.stop();
                    }
                }
            }
        }
    }

    public void beginTurn() {
        turns++;
    }

    public void recordTimeUsed(long time, boolean exception) {
        timeUsed += time;
        if (exception) {
            exceptions++;
        }
    }

    public PlayerStats getStatistics(int position, Map<Node, Double> distanceMap) {
        final PlayerStats stats = new PlayerStats();
        stats.playerId = playerId;
        stats.position = position;
        stats.turns = turns;
        stats.timeUsed = timeUsed;
        stats.exceptions = exceptions;
        stats.hitpoints = hitpoints;
        if (hitpoints <= 0) {
            stats.distance = distanceMap.get(node);
        }
        stats.gridPosition = gridPosition;
        return stats;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof LocalPlayer) {
            return playerId.equals(((LocalPlayer) other).getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return playerId.hashCode();
    }

    public void populate(PlayerState playerState) {
        playerState.setPlayerId(playerId);
        playerState.setNodeId(node.getId());
        playerState.setType(TypeEnum.valueOf(node.getType().name()));
        playerState.setHitpoints(hitpoints);
        playerState.setGear(gear);
        playerState.setStops(curveStops);
        playerState.setLeeway(leeway);
    }

    public void reduceLeeway(long amount) {
        if (amount > Integer.MAX_VALUE) {
            leeway = 0;
        } else {
            leeway = Math.max(0, leeway - (int) amount);
        }
    }

    public int getLeeway() {
        return leeway;
    }
}
