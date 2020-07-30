package gp.ai;

import java.util.*;
import java.util.logging.Level;

import gp.Main;
import gp.model.*;

public class GreatAI implements AI {

    private String playerId;
    private final TrackData data;
    private Map<String, PlayerState> playerMap;
    private Node location;
    private PlayerState player;
    private int gear;
    private Random random = new Random();
    public boolean debug;

    public GreatAI(TrackData data) {
        this.data = data;
    }

    public void init(GameState gameState, int selectedGear) {
        selectGear(gameState);
        gear = selectedGear;
    }

    @Override
    public gp.model.Gear selectGear(GameState gameState) {
        playerMap = AIUtil.buildPlayerMap(gameState);
        player = playerMap.get(playerId);
        if (player == null) {
            throw new RuntimeException("No data sent for player: " + playerId);
        }
        location = data.getNodes().get(player.getNodeId());
        if (location == null) {
            throw new RuntimeException("Unknown location for player: " + playerId);
        }
        final boolean inPits = location.getType() == NodeType.PIT;
        final int maxGear = inPits ? 4 : 6;
        final int stopsDone = player.getStops();
        if (stopsDone < location.getStopCount()) {
            // This can only happen in curves, find max distance to the last curve node.
            final int maxDistance = getMaxDistanceToNextStraight(location) - 1;
            // Select the largest gear for which we might be able to stop in the curve.
            int bestGear = 2; // Never switch to gear 1, as it's intuitively a bad idea.
            for (int gear = Math.min(maxGear, player.getGear() + 1); gear >= 3; gear--) {
                final int[] distribution = Gear.getDistribution(gear);
                if (distribution[distribution.length - 1] >= maxDistance + player.getHitpoints()) {
                    // Rolling max from this dice would cause DNF.
                    continue;
                }
                if (distribution[0] < maxDistance) {
                    bestGear = gear;
                    break;
                }
            }
            if (bestGear == 2) {
                debug("Forced to select low gear 2 because need to stop");
            } else {
                debug("Found gear " + bestGear + " which is good to get out of the curve");
            }
            if (AIUtil.validateGear(player, bestGear, inPits)) {
                gear = bestGear;
            } else {
                // This happens only if it would seem to be a good idea to switch
                // down gears rapidly but we don't have hitpoints for it. This probably
                // means we're going to DNF, but let's at least send a valid move. :(
                gear = player.getGear() - 1;
            }
        } else {
            final int minDistance = getMinDistanceToNextCurve(location);
            if (getStopsRequiredInNextCurve(location) > 1) {
                // We really want to get to the next curve, but just barely. Select smallest
                // gear which guarantees that we get to the next curve. If not possible,
                // select largest gear.
                int bestGear = Math.min(maxGear, player.getGear() + 1);
                int gearCandidate = bestGear;
                // Switching to gear 1 or 2 is intuitively bad idea!?
                boolean found = false;
                while (gearCandidate >= 3) {
                    final int[] distribution = Gear.getDistribution(gearCandidate);
                    if (distribution[0] >= minDistance) {
                        bestGear = gearCandidate;
                        found = true;
                    }
                    gearCandidate--;
                }
                if (found) {
                    debug("Found gear " + bestGear + " which guarantees entrance to next corner");
                } else {
                    debug("Cannot guarantee entrance to next corner, selecting largest possible gear " + bestGear);
                }
                if (AIUtil.validateGear(player, bestGear, inPits)) {
                    gear = bestGear;
                } else {
                    // This happens only if it would seem to be a good idea to switch
                    // down gears rapidly but we don't have hitpoints for it. This probably
                    // means we're going to DNF, but let's at least send a valid move. :(
                    gear = player.getGear() - 1;
                }
            } else {
                // Try to get to the next curve, if possible. Collect all valid gears which are
                // good enough to guarantee entry to next curve. Avoid gear 1, it's horrible.
                final List<Integer> bestGears = new ArrayList<>();
                for (int i = 2; i < Math.min(maxGear, player.getGear() + 1); i++) {
                    final int[] distribution = Gear.getDistribution(i);
                    if (distribution[0] >= minDistance) {
                        bestGears.add(i);
                    }
                }
                if (bestGears.isEmpty()) {
                    // No gear is good enough, just return largest possible gear.
                    gear = Math.min(maxGear, player.getGear() + 1);
                    debug("Cannot guarantee reaching next corner so selecting largest gear " + gear);
                } else if (bestGears.size() == 1) {
                    // Found a single valid gear, use that.
                    gear = bestGears.get(0);
                    debug("Gear " + gear + " is perfect because we will make it to the next corner");
                } else {
                    // Found multiple possibilities, find the gear for which maximum
                    // dice roll is closest to the start of next straight.
                    int maxDistance = !location.isCurve() ? getMaxDistanceToNextStraight(location) : getMaxDistanceToStraightAfterNextCurve(location);
                    int bestGear = Math.min(maxGear, player.getGear() + 1);
                    int score = 100;
                    for (int gear : bestGears) {
                        final int[] distribution = Gear.getDistribution(gear);
                        final int newScore;
                        if (distribution[distribution.length - 1] > maxDistance) {
                            newScore = 2 * (distribution[distribution.length - 1] - maxDistance);
                        } else {
                            newScore = maxDistance - distribution[distribution.length - 1];
                        }
                        if (newScore < score) {
                            score = newScore;
                            bestGear = gear;
                        }
                    }
                    debug("Gear " + bestGear + " is best for covering distance " + maxDistance);
                    gear = bestGear;
                }
                if (!AIUtil.validateGear(player, gear, inPits)) {
                    // This happens only if it would seem to be a good idea to switch
                    // down gears rapidly but we don't have hitpoints for it. This probably
                    // means we're going to DNF, but let's at least send a valid move. :(
                    gear = player.getGear() - 1;
                }
            }
        }
        return new gp.model.Gear().gear(gear);
    }

    public static int getStopsRequiredInNextCurve(Node startNode) {
        final Node nonCurve;
        if (startNode.isCurve()) {
            nonCurve = recurseUntil(startNode, true);
        } else {
            nonCurve = startNode;
        }
        final Node nextCurve = recurseUntil(nonCurve, false);
        return nextCurve.getStopCount();
    }

    private static Node recurseUntil(Node node, boolean isCurve) {
        if (node.isCurve() == isCurve) {
            return recurseUntil(node.childStream().findAny().orElse(null), isCurve);
        } else {
            return node;
        }
    }

    private static int findMaxDistanceToStraight(Node startNode) {
        if (!startNode.isCurve()) {
            return 0;
        }
        return startNode
            .childStream()
            .map(child -> findMaxDistanceToStraight(child))
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0) + 1;
    }

    public static int getMaxDistanceToNextStraight(Node startNode) {
        if (startNode.isCurve()) {
            return findMaxDistanceToStraight(startNode) - 1;
        } else {
            return findDistancesToNextAreaStart(startNode)
                .entrySet()
                .stream()
                .map(e -> findMaxDistanceToStraight(e.getKey()) + e.getValue())
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        }
    }

    public static int getMaxDistanceToStraightAfterNextCurve(Node startNode) {
        if (startNode.isCurve()) {
            return findDistancesToNextAreaStart(startNode)
                .entrySet()
                .stream()
                .map(e -> getMaxDistanceToNextStraight(e.getKey()) + e.getValue())
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        } else {
            return getMaxDistanceToNextStraight(startNode);
        }
    }

    private static Map<Node, Integer> findDistancesToNextAreaStart(Node startNode) {
        final boolean startNodeIsCurve = startNode.isCurve();
        final Deque<Node> work = new ArrayDeque<>();
        final Map<Node, Integer> matchingTypeDistances = new HashMap<>();
        final Map<Node, Integer> nonMatchingTypeDistances = new HashMap<>();
        matchingTypeDistances.put(startNode, 0);
        work.add(startNode);
        while (!work.isEmpty()) {
            final Node node = work.remove();
            node.forEachChild(child -> {
                if (child.isCurve() == startNodeIsCurve) {
                    final Integer distance = matchingTypeDistances.get(child);
                    if (distance == null) {
                        matchingTypeDistances.put(child, matchingTypeDistances.get(node) + 1);
                        work.add(child);
                    }
                } else {
                    final Integer distance = nonMatchingTypeDistances.get(child);
                    if (distance == null) {
                        nonMatchingTypeDistances.put(child, matchingTypeDistances.get(node) + 1);
                    }
                }
            });
        }
        return nonMatchingTypeDistances;
    }

    // Return value 0 would be a bug.
    public static int getMinDistanceToNextCurve(Node startNode) {
        if (startNode.isCurve()) {
            return findDistancesToNextAreaStart(startNode)
                .entrySet()
                .stream()
                .map(e -> e.getKey().getDistanceToNextArea() + e.getValue())
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);
        } else {
            return startNode.getDistanceToNextArea();
        }
    }

    private int getMinDistance(List<Integer> bestIndices, List<ValidMove> moves, Map<Node, Integer> distances) {
        return bestIndices
            .stream()
            .map(index -> distances.get(data.getNodes().get(moves.get(index).getNodeId())))
            .mapToInt(Integer::intValue)
            .min()
            .orElse(0);
    }

    private int getMaxDistance(List<Integer> bestIndices, List<ValidMove> moves, Map<Node, Integer> distances) {
        return bestIndices
            .stream()
            .map(index -> distances.get(data.getNodes().get(moves.get(index).getNodeId())))
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);
    }

    private boolean hasCurve(List<Integer> bestIndices, List<ValidMove> moves) {
        return bestIndices
            .stream()
            .map(i -> data.getNodes().get(moves.get(i).getNodeId()))
            .filter(Node::isCurve)
            .count() > 0;
    }

    private void removeNonCurves(List<Integer> bestIndices, List<ValidMove> moves) {
        final Iterator<Integer> it = bestIndices.iterator();
        while (it.hasNext()) {
            final int i = it.next();
            final Node node = data.getNodes().get(moves.get(i).getNodeId());
            if (!node.isCurve()) {
                it.remove();
            }
        }
    }

    @Override
    public SelectedIndex selectMove(Moves allMoves) {
        if (allMoves.getMoves().isEmpty()) {
            throw new RuntimeException("No valid targets provided by server!");
        }
        final List<ValidMove> moves = allMoves.getMoves();
        final int[] distribution = Gear.getDistribution(gear);
        final Map<Node, Integer> distances = getNodeDistances(location, distribution[distribution.length - 1]);

        // Priority 1: Minimize damage
        int leastDamage = player.getHitpoints();
        final List<Integer> bestIndices = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            final ValidMove vm = moves.get(i);
            final int damage = vm.getBraking() + vm.getOvershoot();
            if (damage < leastDamage) {
                leastDamage = damage;
                bestIndices.clear();
            }
            if (damage <= leastDamage) {
                bestIndices.add(i);
            }
        }

        debug("Minimizing damage, candidates left: " + bestIndices);
        if (hasCurve(bestIndices, moves)) {
            final int stopsInNextCurve = getStopsRequiredInNextCurve(location);
            if (stopsInNextCurve > 1) {
                // Select only curve.
                final int minDistanceToNextCurve = getMinDistanceToNextCurve(location);
                removeNonCurves(bestIndices, moves);
                debug("Selecting only curves, candidates left: " + bestIndices);
                // Minimize distance
                final int minDistance = getMinDistance(bestIndices, moves, distances);
                debug("Min distance to next curve: " + minDistanceToNextCurve + " min distance: " + minDistance);
                final Iterator<Integer> it2 = bestIndices.iterator();
                while (it2.hasNext()) {
                    final int i = it2.next();
                    final Node node = data.getNodes().get(moves.get(i).getNodeId());
                    final int distance = distances.get(node);
                    if (distance > minDistanceToNextCurve && distance > minDistance) {
                        it2.remove();
                    }
                }
                debug("Minimizing distance if next curve is accessible, candidates left: " + bestIndices);
            }
        }

        // Consider pitting
        boolean canPit = bestIndices.stream().map(i -> data.getNodes().get(moves.get(i).getNodeId())).anyMatch(n -> n.getType() == NodeType.PIT);
        if (canPit) {
            final boolean mustPit = bestIndices.stream().map(i -> data.getNodes().get(moves.get(i).getNodeId())).noneMatch(n -> n.getType() != NodeType.PIT);
            if (!mustPit) {
                // There is actually a choice
                final boolean canPitNow = bestIndices.stream().map(i -> data.getNodes().get(moves.get(i).getNodeId())).anyMatch(n -> n.getType() == NodeType.PIT && n.hasGarage());
                if (canPitNow) {
                    final boolean lowHitpoints = player.getHitpoints() <= 12;
                    final Iterator<Integer> it = bestIndices.iterator();
                    while (it.hasNext()) {
                        final int i = it.next();
                        final Node node = data.getNodes().get(moves.get(i).getNodeId());
                        final boolean isPit = node.getType() == NodeType.PIT;
                        if (isPit && !node.hasGarage()) {
                            // Remove all pit nodes with no garage
                            it.remove();
                        } else if (lowHitpoints) {
                            if (!isPit) {
                                // Remove all non-garage nodes if hitpoints are low
                                it.remove();
                            }
                        } else if (isPit) {
                            // Remove all garage nodes if hitpoints are high
                            it.remove();
                        }
                    }
                } else {
                    final boolean lowHitpoints = player.getHitpoints() <= 4;
                    final Iterator<Integer> it = bestIndices.iterator();
                    while (it.hasNext()) {
                        final int i = it.next();
                        final Node node = data.getNodes().get(moves.get(i).getNodeId());
                        final boolean isPit = node.getType() == NodeType.PIT;
                        if (lowHitpoints) {
                            if (!isPit) {
                                // Remove all non-pit nodes if hitpoints are low
                                it.remove();
                            }
                        } else if (isPit) {
                            // Remove all pit nodes if hitpoints are high
                            it.remove();
                        }
                    }
                }
            }
        }

        // Can access curve and need to stop more than once after this move -> enter curve but minimize distance
        if (hasCurve(bestIndices, moves)) {
            final Node curve = recurseUntil(data.getNodes().get(player.getNodeId()), false);
            final int stopCount = curve.getStopCount();
            if (stopCount > player.getStops() + 1) {
                removeNonCurves(bestIndices, moves);
                final int minDistance = getMinDistance(bestIndices, moves, distances);
                final int oldSize = bestIndices.size();
                final Iterator<Integer> it = bestIndices.iterator();
                while (it.hasNext()) {
                    final int i = it.next();
                    final Node node = data.getNodes().get(moves.get(i).getNodeId());
                    final int distance = distances.get(node);
                    if (distance > minDistance) {
                        it.remove();
                    }
                }
                if (bestIndices.size() < oldSize) {
                    debug("Removed " + (oldSize - bestIndices.size()) + " candidates because of stop counts.");
                }
            }
        }

        // Priority 2: Maximize distance
        final int maxDistance = getMaxDistance(bestIndices, moves, distances);
        final Iterator<Integer> it = bestIndices.iterator();
        while (it.hasNext()) {
            final int i = it.next();
            final Node node = data.getNodes().get(moves.get(i).getNodeId());
            final int distance = distances.get(node);
            if (distance < maxDistance) {
                it.remove();
            }
        }
        debug("Maximizing distance, candidates left: " + bestIndices);
        if (hasCurve(bestIndices, moves)) {
            // Priority 3: Enter the curve
            removeNonCurves(bestIndices, moves);
            debug("Considering only curves, candidates left: " + bestIndices);
        } else {
            // Priority 3: Minimize distance to next curve
            final int minDistance = bestIndices
                .stream()
                .map(index -> getMinDistanceToNextCurve(data.getNodes().get(moves.get(index).getNodeId())))
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);
            final Iterator<Integer> it2 = bestIndices.iterator();
            while (it2.hasNext()) {
                final int i = it2.next();
                final Node node = data.getNodes().get(moves.get(i).getNodeId());
                final int distance = getMinDistanceToNextCurve(node);
                if (distance > minDistance) {
                    it2.remove();
                }
            }
            debug("Minimizing distance (" + minDistance + ") to next curve, candidates left: " + bestIndices);
        }
        if (bestIndices.isEmpty()) {
            return new SelectedIndex().index(0);
        }
        return new SelectedIndex().index(bestIndices.get(random.nextInt(bestIndices.size())));
    }

    public static Map<Node, Integer> getNodeDistances(Node startNode, int maxDistance) {
        final Deque<Node> work = new ArrayDeque<>();
        final Map<Node, Integer> distances = new HashMap<>();
        work.add(startNode);
        distances.put(startNode, 0);
        while (!work.isEmpty()) {
            final Node node = work.remove();
            final int distance = distances.get(node);
            if (distance < maxDistance) {
                node.forEachChild(child -> {
                    final Integer childDistance = distances.get(child);
                    if (childDistance == null) {
                        work.add(child);
                        distances.put(child, distance + 1);
                    }
                });
            }
        }
        return distances;
    }

    @Override
    public void notify(Object notification) {
        if (notification instanceof CreatedPlayerNotification) {
            final CreatedPlayerNotification createdPlayer = (CreatedPlayerNotification) notification;
            if (createdPlayer.isControlled()) {
                if (playerId != null) {
                    Main.log.log(Level.SEVERE, "AI assigneed to control multiple players");
                }
                playerId = createdPlayer.getPlayerId();
            }
        }
    }

    private void debug(String msg) {
        if (debug) {
            System.err.println(msg);
        }
    }
}
