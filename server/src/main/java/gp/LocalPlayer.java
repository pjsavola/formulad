package gp;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.swing.JPanel;

import gp.ai.Gear;
import gp.ai.Node;

import gp.ai.NodeType;
import gp.model.*;

public final class LocalPlayer extends Player {
    private UUID id;
    private final List<DamageAndPath> paths = new ArrayList<>();
    private final JPanel panel; // for repaint requests needed for animations
    static int animationDelayInMillis;
    private long timeUsed;
    private int exceptions;
    private int turns;
    private int leeway;
    private int gridPosition;
    private int pitStops;
    private final int maxHitpoints;

    LocalPlayer(String playerId, Node node, double initialAngle, int laps, JPanel panel, int leeway, int maxHitpoints, int[] colors) {
        super(playerId, node, initialAngle, panel, colors);
        lapsToGo = laps;
        this.panel = panel;
        this.leeway = leeway;
        if (node.isCurve()) {
            curveStops = node.getStopCount();
        }
        this.maxHitpoints = maxHitpoints;
        tires = new Tires(Tires.Type.HARD);
        setHitpoints(maxHitpoints);
    }

    public void setId(UUID id) {
        this.id = id;
    }

    void setGridPosition(int gridPosition) {
        this.gridPosition = gridPosition;
    }

    // 1. lowest number of laps to go
    // 2. covered distance of current lap
    // 3. higher gear
    // 4. inside line in curve
    //       curve vs. no-curve -> no-curve is assumed to have inside line and gets player order
    //       curve vs.    curve -> shorter distance to next area is assumed to have inside line and gets player order
    //    no-curve vs. no-curve -> longer distance to next area is assumed to have inside line and gets player order
    // 5. order of arrival
    int compareTo(LocalPlayer player, List<LocalPlayer> stoppedPlayers) {
        if (lapsToGo == player.lapsToGo) {
            if (lapsToGo < 0) {
                final int index1 = stoppedPlayers.indexOf(this);
                final int index2 = stoppedPlayers.indexOf(player);
                return index1 > index2 ? 1 : -1;
            }
            final double d1 = node.getDistance();
            final double d2 = player.node.getDistance();
            if (d1 < d2) return 1;
            else if (d2 < d1) return -1;
            if (gear == player.gear) {
                if (node.isCurve() && !player.node.isCurve()) return 1;
                else if (!node.isCurve() && player.node.isCurve()) return -1;
                final int distanceToNextArea1 = node.getMinDistanceToNextArea();
                final int distanceToNextArea2 = player.node.getMinDistanceToNextArea();
                if (distanceToNextArea1 == distanceToNextArea2) {
                    final int index1 = stoppedPlayers.indexOf(this);
                    final int index2 = stoppedPlayers.indexOf(player);
                    // 1-2 players must be stopped, because otherwise they wouldn't end up in same place.
                    if (index1 == -1) return 1;
                    if (index2 == -1) return -1;
                    return index1 > index2 ? 1 : -1;
                }
                final int delta = distanceToNextArea1 - distanceToNextArea2;
                return node.isCurve() ? delta : -delta;
            }
            return player.gear - gear;
        }
        return lapsToGo - player.lapsToGo;
    }

    public boolean isStopped() {
        return stopped;
    }

    void stop() {
        if (stopped) {
            throw new RuntimeException(getName() + " is stopped twice!");
        }
        if (lapsToGo < 0) {
            Main.log.info(getNameAndId() + " finished the race!");
        } else {
            if (hitpoints > 0) {
                adjustHitpoints(hitpoints, HitpointNotification.Source.CRASH);
            }
            Main.log.info(getNameAndId() + " dropped from the race!");
        }
        gear = 0;
        stopped = true;
    }

    boolean switchGear(int newGear) {
        if (newGear < 1 || newGear > 6) return false;

        if (newGear == gear) return true;

        if (node.getType() == NodeType.PIT && newGear > 4) return false;

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
            adjustHitpoints(damage, HitpointNotification.Source.GEARS);
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
        ((Main) panel).notifyAll(new RollNotification(playerId, gear, roll));
        return roll;
    }

    public void move(int index, int roll) {
        move(paths.get(index), roll);
    }

    private void move(DamageAndPath dp, int roll) {
        final List<Node> route = dp.getPath();
        if (route == null || route.isEmpty()) {
            throw new RuntimeException("Invalid route: " + route);
        }
        if (node != null && route.get(0) != node) {
            throw new RuntimeException("Invalid starting point for route: " + route);
        }
        if (route.size() > roll + 1) {
            if (!tires.canUse()) {
                throw new RuntimeException("Invalid usage of soft tires");
            }
            tires.use();
        }
        final int oldLapsToGo = lapsToGo;
        int size = route.size();
        if (node != null && !node.hasFinish()) {
            for (Node node : route) {
                if (node.hasFinish()) {
                    --lapsToGo;
                    break;
                }
            }
        }
        // TODO: Support for pit lanes elsewhere??
        if (route.get(route.size() - 1).getType() != NodeType.PIT) {
            for (int i = route.size() - 2; i >= 0; --i) {
                if (route.get(i).getType() == NodeType.PIT) {
                    // Car may exit the pits and then cross the Finish line.
                    // Do not count the lap counter twice in that case.
                    if (oldLapsToGo == lapsToGo) {
                        --lapsToGo;
                    }
                    ++pitStops;
                    break;
                }
            }
        }
        for (int i = 1; i < size; ++i) {
            final Node n2 = route.get(i);
            move(n2);
            ((Main) panel).notifyAll(new MovementNotification(playerId, n2.getId()));
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
            adjustHitpoints(dp.getDamage(), HitpointNotification.Source.CURVE);
        }
        if (hitpoints <= 0) {
            Main.log.log(Level.SEVERE, getNameAndId() + " performed an illegal move, taking too much damage");
            stop();
        }
        if (node.hasGarage()) {
            recoverHitpoints();
        }
        if (lapsToGo < 0) {
            stop();
        }
        if (curveStops != oldCurveStops) {
            ((Main) panel).notifyAll(new CurveStopNotification(playerId, curveStops));
        }
        if (lapsToGo != oldLapsToGo) {
            ((Main) panel).notifyAll(new LapChangeNotification(playerId, lapsToGo));
        }
    }

    private void adjustHitpoints(int loss, HitpointNotification.Source source) {
        if (loss > 0) {
            // Show animation
            Main.log.info("Player " + getNameAndId() + " loses " + loss + " hitpoints");
            hitpoints -= loss;
            ((Main) panel).notifyAll(new HitpointNotification(playerId, hitpoints, source));
            ((Game) panel).scheduleHitpointAnimation(loss, this, source);
        }
    }

    private void recoverHitpoints() {
        final int gain = maxHitpoints - hitpoints;
        if (gain > 0) {
            // Show animation
            Main.log.info("Player " + getNameAndId() + " pits and recovers full hitpoints");
            hitpoints += gain;
            ((Main) panel).notifyAll(new HitpointNotification(playerId, hitpoints, HitpointNotification.Source.PITS));
            ((Game) panel).scheduleHitpointAnimation(-gain, this, HitpointNotification.Source.PITS);
        }
    }

    Moves findAllTargets(int roll, String gameId, List<LocalPlayer> players) {
        int braking = tires != null && tires.canUse() ? -1 : 0;
        final Set<Node> forbiddenNodes = players
            .stream()
            .map(player -> player.node)
            .collect(Collectors.toSet());
        paths.clear();
        final List<ValidMove> validMoves = new ArrayList<>();
        while (braking < hitpoints) {
            final Map<Node, DamageAndPath> targets = findTargetNodes(roll - braking, forbiddenNodes);
            for (Map.Entry<Node, DamageAndPath> e : targets.entrySet()) {
                final int damage = e.getValue().getDamage() + Math.max(0, braking);
                if (damage < hitpoints) {
                    validMoves.add(new ValidMove()
                        .nodeId(e.getKey().getId())
                        .overshoot(e.getValue().getDamage() - Math.min(0, braking))
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
        return NodeUtil.findTargetNodes(node, gear, roll, hitpoints, curveStops, lapsToGo, forbiddenNodes);
    }

    static void possiblyAddEngineDamage(List<LocalPlayer> players, Random rng) {
        Main.log.info("20 or 30 was rolled, possibly adding engine damage for all players on gear 5 or 6");
        for (LocalPlayer player : players) {
            if (player.gear == 5 || player.gear == 6) {
                if (rng.nextInt(20) < 4) {
                    player.adjustHitpoints(1, HitpointNotification.Source.ENGINE);
                    if (player.hitpoints <= 0) {
                        player.stop();
                    }
                }
            }
        }
    }

    void collide(List<LocalPlayer> players, Map<Node, Set<Node>> adjacentNodes, Random rng) {
        for (LocalPlayer player : players) {
            if (player.isStopped()) {
                continue;
            }
            if (player != this && adjacentNodes.get(node).contains(player.node)) {
                Main.log.info(getNameAndId() + " is close to " + player.getNameAndId() + " and may collide");
                if (!isStopped() && rng.nextInt(20) < 4) {
                    adjustHitpoints(1, HitpointNotification.Source.COLLISION);
                    if (hitpoints <= 0) {
                        stop();
                    }
                }
                if (rng.nextInt(20) < 4) {
                    player.adjustHitpoints(1, HitpointNotification.Source.COLLISION);
                    if (player.hitpoints <= 0) {
                        player.stop();
                    }
                }
            }
        }
    }

    void beginTurn() {
        turns++;
    }

    void recordTimeUsed(long time, boolean exception) {
        timeUsed += time;
        if (exception) {
            exceptions++;
        }
    }

    PlayerStats getStatistics(int position) {
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
            stats.distance = node.getDistance();
        }
        stats.gridPosition = gridPosition;
        stats.pitStops = pitStops;
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

    void populate(PlayerState playerState) {
        playerState.setPlayerId(playerId);
        playerState.setNodeId(node.getId());
        playerState.setHitpoints(hitpoints);
        playerState.setGear(gear);
        playerState.setStops(curveStops);
        playerState.setLeeway(leeway);
        playerState.setLapsToGo(lapsToGo);
        playerState.setTires(tires);
    }

    void reduceLeeway(long amount) {
        if (amount > Integer.MAX_VALUE) {
            leeway = 0;
        } else {
            leeway = Math.max(0, leeway - (int) amount);
        }
    }

    int getLeeway() {
        return leeway;
    }

    void changeTires(Tires tires) {
        if (tires != null && tires != this.tires) {
            if (gear == 0 || node.isPit()) {
                this.tires = tires;
                ((Main) panel).notifyAll(new TireChangeNotification(playerId, tires));
            }
        }
    }
}
