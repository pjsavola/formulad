package formulad;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.swing.JPanel;

import formulad.ai.Gear;
import formulad.ai.Node;

import formulad.model.*;

public final class LocalPlayer extends Player {
    private UUID id;
    private int lapsToGo;
    private final List<DamageAndPath> paths = new ArrayList<>();
    private final JPanel panel; // for repaint requests needed for animations
    public static int animationDelayInMillis;
    private long timeUsed;
    private int exceptions;
    private int turns;
    private int leeway;
    private int gridPosition;

    public LocalPlayer(String playerId, Node node, double initialAngle, int laps, JPanel panel, int leeway, int color1, int color2) {
        super(playerId, node, initialAngle, panel, color1, color2);
        lapsToGo = laps;
        this.panel = panel;
        this.leeway = leeway;
    }

    public void setId(UUID id) {
        this.id = id;
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
            throw new RuntimeException(getName() + " is stopped twice!");
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

    public boolean switchGear(int newGear) {
        if (newGear < 1 || newGear > 6) return false;

        if (newGear == gear) return true;

        if (node.getType() == Node.Type.PIT && newGear > 4) return false;

        if (Math.abs(newGear - gear) <= 1) {
            setGear(newGear);
            panel.repaint();
            try {
                Thread.sleep(animationDelayInMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }

        // downwards more than 1
        final int damage = gear - newGear - 1;
        if (damage > 0 && damage < 4 && hitpoints > damage) {
            adjustHitpoints(damage);
            setGear(newGear);
            panel.repaint();
            try {
                Thread.sleep(animationDelayInMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public int roll(Random rng) {
        if (gear == 0) return 0;
        final int[] distribution = Gear.getDistribution(gear);
        final int roll = distribution[rng.nextInt(distribution.length)];
        ((FormulaD) panel).notifyAll(new RollNotification(playerId, gear, roll));
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
        if (route.get(route.size() - 1).getType() != Node.Type.PIT) {
            for (int i = route.size() - 2; i >= 0; --i) {
                if (route.get(i).getType() == Node.Type.PIT) {
                    lapsToGo--;
                    break;
                }
            }
        }
        for (int i = 1; i < size; ++i) {
            final Node n2 = route.get(i);
            move(n2, coordinates);
            ((FormulaD) panel).notifyAll(new MovementNotification(playerId, n2.getId()));
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
        if (node.getType() == Node.Type.PIT) {
            recoverHitpoints();
        }
        if (lapsToGo < 0) {
            stop();
        }
        if (curveStops != oldCurveStops) {
            ((FormulaD) panel).notifyAll(new CurveStopNotification(playerId, curveStops));
        }
        if (lapsToGo != oldLapsToGo) {
            ((FormulaD) panel).notifyAll(new LapChangeNotification(playerId, lapsToGo));
        }
    }

    private void adjustHitpoints(int loss) {
        FormulaD.log.info("Player " + getNameAndId() + " loses " + loss + " hitpoints");
        // Show animation
        int counter = loss;
        while (counter-- > 0) {
            hitpoints--;
            ((FormulaD) panel).notifyAll(new HitpointNotification(playerId, hitpoints));
            panel.repaint();
            try {
                Thread.sleep(animationDelayInMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void recoverHitpoints() {
        FormulaD.log.info("Player " + getNameAndId() + " pits and recovers full hitpoints");
        // Show animation
        while (hitpoints < 18) {
            hitpoints++;
            ((FormulaD) panel).notifyAll(new HitpointNotification(playerId, hitpoints));
            panel.repaint();
            try {
                Thread.sleep(animationDelayInMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
        final boolean finalLap = lapsToGo == 0;
        final boolean allowPitEntry = !finalLap && gear < 5;
        final Map<Node, DamageAndPath> result = NodeUtil.findNodes(node, roll, forbiddenNodes, true, curveStops, finalLap, allowPitEntry);
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
        stats.id = id;
        stats.position = position;
        stats.turns = turns;
        stats.lapsToGo = lapsToGo;
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
            return getId().equals(((LocalPlayer) other).getId());
        }
        return false;
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
