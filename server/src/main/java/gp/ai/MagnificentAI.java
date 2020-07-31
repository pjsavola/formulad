package gp.ai;

import gp.model.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class MagnificentAI extends BaseAI {

    private Map<String, PlayerState> playerMap;
    private Node location;
    private PlayerState player;
    private int gear;
    private Random random = new Random();
    public boolean debug;

    public MagnificentAI(TrackData data) {
        super(data);
    }

    private static Pair<Integer, Integer> findGarage(Node node) {
        int min = -1;
        int max = -1;
        if (node.isPit()) {
            Node pitLaneNode = node;
            int len = 0;
            while (pitLaneNode != null) {
                if (pitLaneNode.hasGarage()) {
                    max = len;
                    if (min == -1) min = len;
                }
                pitLaneNode = pitLaneNode.childStream().filter(Node::isPit).findAny().orElse(null);
                ++len;
            }
        }
        return Pair.of(min, max);
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
        final Set<Node> blockedNodes = playerMap
                .values()
                .stream()
                .map(p -> data.getNodes().get(p.getNodeId()))
                .collect(Collectors.toSet());
        final int minGear = Math.max(1, player.getGear() - Math.min(4, player.getHitpoints() - 1));
        final int maxGear = Math.min(location.isPit() ? 4 : 6, player.getGear() + 1);
        final int stopsDone = player.getStops();
        final int stopsToDo = location.getStopCount() - stopsDone;
        final int movePermit = AIUtil.getMaxDistanceWithoutDamage(location, stopsDone, blockedNodes);
        final int movePermitWithoutOthers = AIUtil.getMaxDistanceWithoutDamage(location, stopsDone, Collections.emptySet());
        final int minDistanceToNextCurve = AIUtil.getMinDistanceToNextCurve(location, blockedNodes);
        int minRoll = Gear.getMin(minGear); // Strict minimum
        int maxRoll = Gear.getMax(maxGear); // Strict maximum
        maxRoll = Math.min(maxRoll, movePermit + player.getHitpoints() - 1);
        int idealRoll = movePermit;
        if (location.isPit()) {
            if (player.getHitpoints() < 18) {
                final Pair<Integer, Integer> garageDistances = findGarage(location);
                final int min = garageDistances.getLeft();
                final int max = garageDistances.getRight();
                if (min != -1) minRoll = Math.max(minRoll, min);
                if (max != -1) maxRoll = Math.min(maxRoll, max + player.getHitpoints() - 1);
                idealRoll = Math.min(maxRoll, movePermit);
            }
        } else if (location.isCurve() && stopsToDo > 0) {
            if (stopsToDo > 1) {
                idealRoll = Math.min(movePermit, movePermitWithoutOthers / stopsToDo);
            }
        } else if (minDistanceToNextCurve != -1 && maxRoll >= minDistanceToNextCurve && movePermit >= minDistanceToNextCurve) {
            minRoll = Math.max(minRoll, minDistanceToNextCurve);
            if (AIUtil.getStopsRequiredInNextCurve(location) > 1) {
                idealRoll = minRoll;
            }
        }
        // TODO: Decide when it's a good idea to visit pits if you're not in pits yet.
        final int distanceToPits = AIUtil.getMinDistanceToPits(location, blockedNodes);
        System.out.println(minRoll + " - " + idealRoll + " - " + maxRoll);
        final int max = Gear.getMax(maxGear);
        final int min = Gear.getMin(minGear);
        if (max <= minRoll) {
            if (debug) System.out.println("Trivial decision max gear: " + maxGear);
            gear = maxGear;
        } else if (min >= maxRoll) {
            if (debug) System.out.println("Trivial decision min gear: " + minGear);
            gear = minGear;
        } else {
            int bestGear = minGear;
            int bestScore = Integer.MIN_VALUE;
            for (int gear = minGear; gear <= maxGear; ++gear) {
                int score = 30;
                final int gearMin = Gear.getMin(gear);
                final int gearMax = Gear.getMax(gear);
                if (gearMin >= minRoll) score += 10;
                if (gearMax <= maxRoll) score += 7;
                final int avg = (gearMin + gearMax) / 2;
                score -= Math.abs(avg - idealRoll);
                if (score >= bestScore) {
                    bestScore = score;
                    bestGear = gear;
                }
                if (debug) System.out.println("Gear " + gear + " has score " + score);
            }
            if (debug) System.out.println("Selected: " + bestGear);
            gear = bestGear;
        }
        return new gp.model.Gear().gear(gear);
    }

    // Return value 0 would be a bug.
    public static int getMinDistanceToNextCurve(Node startNode) {
        if (startNode.isCurve()) {
            return AIUtil.findMinDistancesToNextAreaStart(startNode, false)
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
            .anyMatch(Node::isCurve);
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
            final int stopsInNextCurve = AIUtil.getStopsRequiredInNextCurve(location);
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
            final Node curve = AIUtil.recurseWhile(data.getNodes().get(player.getNodeId()), false, data.getNodes().get(player.getNodeId()).isPit());
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

    private void debug(String msg) {
        if (debug) {
            System.err.println(msg);
        }
    }
}
