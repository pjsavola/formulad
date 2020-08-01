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

    private static int getNextGear(int gearMask) {
        double digitCount = ((int) Math.log10(gearMask)) + 1;
        int nextGear = gearMask;
        while (--digitCount > 1) {
            nextGear = nextGear / 10;
        }
        return nextGear % 10;
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
        if (player.getGear() == 0) {
            gear = 1;
            return new gp.model.Gear().gear(1);
        }
        final Set<Node> blockedNodes = playerMap
                .values()
                .stream()
                .map(p -> data.getNodes().get(p.getNodeId()))
                .collect(Collectors.toSet());
        gearMaskToScores.clear();
        gearMaskToMaxScore.clear();
        gearMaskToMinScore.clear();
        final GearEvaluator evaluator = new GearEvaluator(location, blockedNodes, player.getGear(), player.getStops(), player.getHitpoints());
        for (int i = 0; i < 1; ++i) {
            evaluator.randomWalk();
        }
        final Map<Integer, Integer> gearToMaxScore = new HashMap<>();
        gearMaskToMaxScore.forEach((gearMask, score) -> {
            final int nextGear = getNextGear(gearMask);
            final Integer oldScore = gearToMaxScore.get(nextGear);
            if (oldScore == null || score > oldScore) gearToMaxScore.put(nextGear, score);
        });
        final int riskGear = gearToMaxScore.entrySet().stream().max((e1, e2) -> e1.getValue() - e2.getValue()).map(e -> e.getKey()).orElse(-1);
        final int avgGear = gearMaskToScores.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().stream().mapToInt(Integer::intValue).sum() / e.getValue().size())).map(e -> e.getKey()).map(MagnificentAI::getNextGear).orElse(-1);
        final int safeGear = gearMaskToMinScore.entrySet().stream().max((e1, e2) -> e1.getValue() - e2.getValue()).map(e -> e.getKey()).map(MagnificentAI::getNextGear).orElse(-1);
        System.out.println("Risk gear: " + riskGear);
        System.out.println("Safe gear: " + safeGear);
        System.out.println("Best gear: " + avgGear);

        if (true) return new gp.model.Gear().gear(avgGear);
        // Find gear sequences for:
        // - max of mins (safe option)
        // - min of maxs (risky option)
        // - max of averages

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
        if (debug) System.out.println(minRoll + " - " + idealRoll + " - " + maxRoll);
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
            final int[] initialScores = { 0, 3, 6, 7, 8, 4 }; // may depend on damage
            for (int gear = minGear; gear <= maxGear; ++gear) {
                int score = Math.min(player.getHitpoints(), initialScores[gear - 1]);
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

    private Random r = new Random();

    private Map<Integer, List<Integer>> gearMaskToScores = new HashMap<>();
    private Map<Integer, Integer> gearMaskToMaxScore = new HashMap<>();
    private Map<Integer, Integer> gearMaskToMinScore = new HashMap<>();

    class GearEvaluator {
        private final int turns;
        private final int gear;
        private final int gearMask;
        private final int hitpoints;
        private final int minGear;
        private final int maxGear;
        private final int stopsToDo;
        private final int stopsInNextCurve;
        private final int movePermit;
        private final int movePermitWithoutOthers;
        private final int movePermitToNextCornerWithoutOthers;
        private final int minMovesToTakeDamageWithoutOthers;
        private final int minDistanceToNextCurve;
        private final int minDistanceToNextCurveWithoutOthers;
        private final boolean inPits;
        private final boolean enteredNextCurve;

        public String toString() {
            String s = "Sequence " + gearMask + "\n";
            s += "Turns: " + turns + "\n";
            s += "Gear: " + gear + "\n";
            s += "Hitpoints: " + hitpoints + "\n";
            s += "Min/Max Gear: " + minGear + "-" + maxGear + "\n";
            s += "Stops to do: " + stopsToDo + "\n";
            s += "Stops in next curve: " + stopsInNextCurve + "\n";
            s += "Move permit: " + movePermit + "\n";
            s += "Move permit without others: " + movePermitWithoutOthers + "\n";
            s += "Move permit to next corner without others: " + movePermitToNextCornerWithoutOthers + "\n";
            s += "Min move to take damage without others: " + minMovesToTakeDamageWithoutOthers + "\n";
            s += "Min distance to next curve: " + minDistanceToNextCurve + "\n";
            s += "Min distance to next curve without others: " + minDistanceToNextCurveWithoutOthers + "\n";
            s += "Entered next curve: " + enteredNextCurve + "\n";
            s += "SCORE: " + getScore() + "\n";
            return s;
        }

        private static final int searchDepth = 4;

        private GearEvaluator(Node location, Set<Node> blockedNodes, int gear, int stopCount, int hitpoints) {
            turns = 0;
            this.gear = gear;
            gearMask = gear;
            this.hitpoints = hitpoints;
            inPits = location.isPit();
            minGear = Math.max(1, gear - Math.min(4, hitpoints - 1));
            maxGear = Math.min(inPits ? 4 : 6, gear + 1);
            stopsToDo = location.getStopCount() - stopCount;
            movePermit = AIUtil.getMaxDistanceWithoutDamage(location, stopCount, blockedNodes);
            movePermitWithoutOthers = AIUtil.getMaxDistanceWithoutDamage(location, stopCount, Collections.emptySet());
            movePermitToNextCornerWithoutOthers = AIUtil.getMaxDistanceWithoutDamage(location, location.getStopCount(), Collections.emptySet());
            minDistanceToNextCurve = AIUtil.getMinDistanceToNextCurve(location, blockedNodes);
            minDistanceToNextCurveWithoutOthers = AIUtil.getMinDistanceToNextCurve(location, Collections.emptySet());
            minMovesToTakeDamageWithoutOthers = AIUtil.getMinDistanceToTakeDamage(location, stopCount);
            stopsInNextCurve = AIUtil.getStopsRequiredInNextCurve(location);
            enteredNextCurve = false;
        }

        private GearEvaluator(GearEvaluator old,
                              int gear,
                              int moveSteps,
                              boolean inPits) {
            // TODO: Calculate new movePermit...
            turns = old.turns + 1;
            this.gear = gear;
            gearMask = 10 * old.gearMask + gear;
            minDistanceToNextCurve = old.minDistanceToNextCurveWithoutOthers - moveSteps;
            final int damage = Math.max(0, moveSteps - ((old.stopsToDo > 0 && minDistanceToNextCurve >= 1) ? old.movePermit : old.movePermitToNextCornerWithoutOthers)) + Math.max(0, old.gear - gear - 1);
            minDistanceToNextCurveWithoutOthers = old.minDistanceToNextCurveWithoutOthers - moveSteps;
            enteredNextCurve = minDistanceToNextCurve < 1;
            hitpoints = old.hitpoints - damage;
            minGear = Math.max(1, gear - Math.min(4, hitpoints - 1));
            maxGear = Math.min(inPits ? 4 : 6, gear + 1);
            stopsToDo = enteredNextCurve ? old.stopsInNextCurve - 1 : old.stopsToDo - 1;
            stopsInNextCurve = enteredNextCurve ? -1 : old.stopsInNextCurve;
            movePermit = old.movePermitWithoutOthers - moveSteps;
            movePermitWithoutOthers = old.movePermitWithoutOthers - moveSteps;
            movePermitToNextCornerWithoutOthers = old.movePermitToNextCornerWithoutOthers - moveSteps;
            minMovesToTakeDamageWithoutOthers = old.minMovesToTakeDamageWithoutOthers - moveSteps;
            this.inPits = inPits;
        }

        private boolean canEvaluateNext() {
            return minDistanceToNextCurveWithoutOthers > 0 || stopsToDo > 0;
        }

        private void randomWalk() {
            if (!canEvaluateNext() || gearMask > Math.pow(10, searchDepth)) {
                final int score = getScore();
                gearMaskToScores.computeIfAbsent(gearMask, gm -> new ArrayList<>()).add(score);
                final Integer min = gearMaskToMinScore.get(gearMask);
                final Integer max = gearMaskToMaxScore.get(gearMask);
                if (min == null || score < min) gearMaskToMinScore.put(gearMask, score);
                if (max == null || score > max) gearMaskToMaxScore.put(gearMask, score);
                //System.out.println(toString());
            } else {
                int i = minGear;
                if (minDistanceToNextCurve > 0 && stopsToDo <= 0) {
                    while (Gear.getMax(i) < minDistanceToNextCurve) {
                        if (i == maxGear) {
                            break;
                        }
                        ++i;
                    }
                }
                boolean canBreak = false;
                while (i <= maxGear) {
                    if (!AIUtil.validateGear(hitpoints, gear, i, inPits)) continue;
                    if (canBreak) {
                        if (movePermitToNextCornerWithoutOthers < Gear.getMin(i)) break; // maybe something else in final corner(s)?
                        if (stopsToDo > 0 && !enteredNextCurve && movePermit < Gear.getMin(i)) break;
                    }
                    final int[] distribution = Gear.getDistribution(i);
                    final int roll = distribution[r.nextInt(distribution.length)];
                    final GearEvaluator next = new GearEvaluator(this, i, roll, inPits);
                    next.randomWalk();
                    canBreak = true;
                    ++i;
                }
            }
        }

        private List<GearEvaluator> nextTurn() {
            final List<GearEvaluator> candidates = new ArrayList<>();
            if (canEvaluateNext()) {
                int i = minGear;
                if (minDistanceToNextCurve > 0 && stopsToDo <= 0 && Gear.getMax(maxGear) <= minDistanceToNextCurve) {
                    i = maxGear;
                }
                while (i <= maxGear) {
                    if (!AIUtil.validateGear(hitpoints, gear, i, inPits)) continue;
                    final int[] distribution = Gear.getDistribution(i);
                    for (int moveSteps : distribution) {
                        final GearEvaluator next = new GearEvaluator(this, i, moveSteps, false);
                        if (next.hitpoints <= 0) continue;
                        candidates.add(next);
                    }
                    ++i;
                }
            }
            return candidates;
        }

        private int getScore() {
            int score = canEvaluateNext() ? 0 : 100;
            score -= 10 * (turns + stopsToDo);
            score += 2 * hitpoints;
            final int[] initialScores = { 0, 3, 6, 8, 8, 4 };
            score += initialScores[gear - 1];
            if (stopsToDo > 0) {
                score += enteredNextCurve ? movePermitToNextCornerWithoutOthers : movePermitWithoutOthers;
            } else if (!enteredNextCurve) {
                score -= Math.max(0, minMovesToTakeDamageWithoutOthers - 1);
            } else {
                score -= Math.max(0, movePermitToNextCornerWithoutOthers) / 2;
            }
            return score;
        }

        private Pair<Integer, Integer> getMinMaxScore(Deque<GearEvaluator> stack) {
            final GearEvaluator evaluator = stack.getLast();
            final List<GearEvaluator> nextEvaluators = evaluator.nextTurn();
            if (nextEvaluators.isEmpty()) {
                final int score = evaluator.getScore();
                return Pair.of(score, score);
            } else {
                final int maxScore = nextEvaluators.stream().map(nextEvaluator -> {
                    stack.addLast(nextEvaluator);
                    final int score = getMinMaxScore(stack).getRight();
                    stack.removeLast();
                    return score;
                }).mapToInt(Integer::intValue).max().orElse(Integer.MIN_VALUE);
                final int minScore = nextEvaluators.stream().map(nextEvaluator -> {
                    stack.addLast(nextEvaluator);
                    final int score = getMinMaxScore(stack).getLeft();
                    stack.removeLast();
                    return score;
                }).mapToInt(Integer::intValue).min().orElse(Integer.MAX_VALUE);
                return Pair.of(minScore, maxScore);
            }
        }
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
