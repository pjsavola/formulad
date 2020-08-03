package gp.ai;

import gp.model.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class MagnificentAI extends BaseAI {

    private Map<String, PlayerState> playerMap;
    private Node location;
    private PlayerState player;
    private int gear;
    private Random random = new Random();
    public boolean debug = true;

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
        location = nodes.get(player.getNodeId());
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
                .map(p -> nodes.get(p.getNodeId()))
                .collect(Collectors.toSet());
        final Set<Node> pitNodes = nodes.stream().filter(Node::isPit).collect(Collectors.toSet());
        gearMaskToScores.clear();
        gearMaskToMaxScore.clear();
        gearMaskToMinScore.clear();
        final GearEvaluator evaluator = new GearEvaluator(location, blockedNodes, pitNodes, player.getGear(), player.getStops(), player.getHitpoints(), player.getLapsToGo());
        for (int i = 0; i < 100; ++i) {
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
        private final int garageMin;
        private final int garageMax;
        private final int searchDepth;

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

        private GearEvaluator(Node location, Set<Node> blockedNodes, Set<Node> pitNodes, int gear, int stopCount, int hitpoints, int lapsToGo) {
            turns = 0;
            this.gear = gear;
            gearMask = gear;
            this.hitpoints = hitpoints;
            inPits = location.isPit();
            minGear = Math.max(1, gear - Math.min(4, hitpoints - 1));
            stopsToDo = location.getStopCount() - stopCount;
            final int minDistanceToPits = AIUtil.getMinDistanceToPits(location, blockedNodes);
            if (!inPits) blockedNodes.addAll(pitNodes);
            movePermit = AIUtil.getMaxDistanceWithoutDamage(location, stopCount, blockedNodes);
            movePermitWithoutOthers = AIUtil.getMaxDistanceWithoutDamage(location, stopCount, !inPits ? pitNodes : Collections.emptySet());
            movePermitToNextCornerWithoutOthers = AIUtil.getMaxDistanceWithoutDamage(location, location.getStopCount(), !inPits ? pitNodes : Collections.emptySet());
            minDistanceToNextCurve = AIUtil.getMinDistanceToNextCurve(location, blockedNodes);
            minDistanceToNextCurveWithoutOthers = AIUtil.getMinDistanceToNextCurve(location, !inPits ? pitNodes : Collections.emptySet());
            minMovesToTakeDamageWithoutOthers = AIUtil.getMinDistanceToTakeDamage(location, stopCount);
            stopsInNextCurve = AIUtil.getStopsRequiredInNextCurve(location);
            enteredNextCurve = false;
            if (location.isPit()) {
                final Pair<Integer, Integer> p = findGarage(location);
                garageMin = p.getLeft();
                garageMax = p.getRight();
            } else {
                garageMin = -1;
                garageMax = -1;
            }
            final int maxGear = Math.min(inPits ? 4 : 6, gear + 1);
            if (lapsToGo > 0 && minDistanceToPits < movePermit && minDistanceToPits < Gear.getMin(maxGear) && hitpoints < r.nextInt(18) && minGear <= 4) {
                System.out.println("Decided to pit");
                this.maxGear = 4;
                searchDepth = 1;
            } else {
                this.maxGear = maxGear;
                searchDepth = inPits ? 1 : 4;
            }
        }

        private GearEvaluator(GearEvaluator old,
                              int gear,
                              int moveSteps,
                              boolean inPits) {
            turns = old.turns + 1;
            this.gear = gear;
            gearMask = 10 * old.gearMask + gear;
            minDistanceToNextCurve = old.minDistanceToNextCurveWithoutOthers - moveSteps;
            final int damage = Math.max(0, moveSteps - ((old.stopsToDo > 0 && minDistanceToNextCurve >= 1) ? old.movePermit : old.movePermitToNextCornerWithoutOthers)) + Math.max(0, old.gear - gear - 1);
            minDistanceToNextCurveWithoutOthers = old.minDistanceToNextCurveWithoutOthers - moveSteps;
            enteredNextCurve = minDistanceToNextCurve < 1;
            hitpoints = (inPits && moveSteps >= old.garageMin && moveSteps <= old.garageMax) ? 18 : old.hitpoints - damage;
            garageMin = old.garageMin - moveSteps;
            garageMax = old.garageMax - moveSteps;
            minGear = Math.max(1, gear - Math.min(4, hitpoints - 1));
            maxGear = Math.min(inPits ? 4 : 6, gear + 1);
            stopsToDo = enteredNextCurve ? old.stopsInNextCurve - 1 : old.stopsToDo - 1;
            stopsInNextCurve = enteredNextCurve ? -1 : old.stopsInNextCurve;
            movePermit = old.movePermitWithoutOthers - moveSteps;
            movePermitWithoutOthers = old.movePermitWithoutOthers - moveSteps;
            movePermitToNextCornerWithoutOthers = old.movePermitToNextCornerWithoutOthers - moveSteps;
            minMovesToTakeDamageWithoutOthers = old.minMovesToTakeDamageWithoutOthers - moveSteps;
            this.inPits = inPits;
            searchDepth = old.searchDepth;
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
            score -= 10 * turns;
            score += 2 * hitpoints;
            final int[] initialScores = { 0, 6, 9, 11, 12, 8 };
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
                .map(e -> e.getKey().getMinDistanceToNextArea() + e.getValue())
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);
        } else {
            return startNode.getMinDistanceToNextArea();
        }
    }

    private int getMinDistance(List<Integer> bestIndices, List<ValidMove> moves, Map<Node, Integer> distances) {
        return bestIndices
            .stream()
            .map(index -> distances.get(nodes.get(moves.get(index).getNodeId())))
            .mapToInt(Integer::intValue)
            .min()
            .orElse(0);
    }

    private int getMaxDistance(List<Integer> bestIndices, List<ValidMove> moves, Map<Node, Integer> distances) {
        return bestIndices
            .stream()
            .map(index -> distances.get(nodes.get(moves.get(index).getNodeId())))
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);
    }

    private boolean hasCurve(List<Integer> bestIndices, List<ValidMove> moves) {
        return bestIndices
            .stream()
            .map(i -> nodes.get(moves.get(i).getNodeId()))
            .anyMatch(Node::isCurve);
    }

    private void removeNonCurves(List<Integer> bestIndices, List<ValidMove> moves) {
        final Iterator<Integer> it = bestIndices.iterator();
        while (it.hasNext()) {
            final int i = it.next();
            final Node node = nodes.get(moves.get(i).getNodeId());
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
        int bestGarageIndex = -1;
        final List<Integer> bestIndices = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            final ValidMove vm = moves.get(i);
            final int damage = vm.getBraking() + vm.getOvershoot();
            final Node node = nodes.get(vm.getNodeId());
            if (node.hasGarage()) {
                if (bestGarageIndex == -1 || node.getDistance() > nodes.get(moves.get(bestGarageIndex).getNodeId()).getDistance()) {
                    bestGarageIndex = i;
                }
            }
            if (damage < leastDamage) {
                leastDamage = damage;
                bestIndices.clear();
            }
            if (damage <= leastDamage) {
                bestIndices.add(i);
            }
        }
        if (bestGarageIndex != -1 && !bestIndices.contains(bestGarageIndex)) {
            bestIndices.add(bestGarageIndex);
        }
        debug("Minimizing damage, candidates left: " + bestIndices);

        if (bestIndices.size() == 1) return new SelectedIndex().index(bestIndices.get(0));

        // Consider pitting
        boolean canPit = bestIndices.stream().map(i -> nodes.get(moves.get(i).getNodeId())).anyMatch(n -> n.getType() == NodeType.PIT);
        if (canPit) {
            debug("Considering pitting");
            final boolean mustPit = bestIndices.stream().map(i -> nodes.get(moves.get(i).getNodeId())).noneMatch(n -> n.getType() != NodeType.PIT);
            if (!mustPit) {
                // There is actually a choice
                final boolean canPitNow = bestIndices.stream().map(i -> nodes.get(moves.get(i).getNodeId())).anyMatch(n -> n.getType() == NodeType.PIT && n.hasGarage());
                if (canPitNow) {
                    final boolean lowHitpoints = player.getHitpoints() <= 12;
                    final Iterator<Integer> it = bestIndices.iterator();
                    while (it.hasNext()) {
                        final int i = it.next();
                        final Node node = nodes.get(moves.get(i).getNodeId());
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
                        final Node node = nodes.get(moves.get(i).getNodeId());
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

        if (bestIndices.size() == 1) return new SelectedIndex().index(bestIndices.get(0));

        boolean minimizeDistanceForNextCurve = false;
        if (hasCurve(bestIndices, moves)) {
            final boolean needToStop = location.isCurve() && location.getStopCount() > player.getStops();
            final boolean minimize;
            if (needToStop) {
                minimize = location.getStopCount() > player.getStops() + 1;
            } else {
                final boolean thisCurve;
                if (location.isCurve()) {
                    // Make sure that bestIndices are not for the current curve...
                    final Node straight = AIUtil.recurseWhile(location, true, false);
                    final double min = location.getDistance();
                    final double max = straight.getDistance();
                    thisCurve = bestIndices
                            .stream()
                            .map(i -> nodes.get(moves.get(i).getNodeId()))
                            .filter(Node::isCurve)
                            .map(Node::getDistance)
                            .anyMatch(d -> d >= min && d <= max);
                } else {
                    thisCurve = false;
                }
                if (thisCurve) {
                    minimize = false;
                } else {
                    final int stopsInNextCurve = AIUtil.getStopsRequiredInNextCurve(location);
                    minimize = stopsInNextCurve > 1;
                }
            }
            if (minimize) {
                removeNonCurves(bestIndices, moves);
                // Maximize next turn move permit only up to the max roll of next turn.
                final int limit = Gear.getMax(player.getGear() + 1);
                final boolean useLimit = player.getStops() + 1 == location.getStopCount();
                final int maxMovePermit = bestIndices
                        .stream()
                        .map(i -> nodes.get(moves.get(i).getNodeId()))
                        .map(n -> AIUtil.getMaxDistanceWithoutDamage(n, 0, Collections.emptySet()))
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0);
                debug("Minimizing distance. (max distance without damage " + maxMovePermit + ")");
                bestIndices.removeIf(i -> {
                    final Node node = nodes.get(moves.get(i).getNodeId());
                    final int movePermit = AIUtil.getMaxDistanceWithoutDamage(node, 0, Collections.emptySet());
                    if (useLimit && movePermit >= limit) return false;
                    return movePermit < maxMovePermit;
                });
            } else {
                final double maxDistance = bestIndices
                        .stream()
                        .map(i -> nodes.get(moves.get(i).getNodeId()))
                        .filter(Node::isCurve)
                        .map(Node::getDistance)
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0);
                debug("Maximizing distance for the curve. (max " + maxDistance + ")");
                bestIndices.removeIf(i -> {
                    final Node node = nodes.get(moves.get(i).getNodeId());
                    return node.getDistance() < maxDistance;
                });
                minimizeDistanceForNextCurve = true;
            }
            debug("Curve optimized, candidates left: " + bestIndices);
        }

        if (bestIndices.size() == 1) return new SelectedIndex().index(bestIndices.get(0));

        // Priority 2: Maximize or minimize distance depending on stops required
        if (minimizeDistanceForNextCurve || !hasCurve(bestIndices, moves)) {
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
            debug("No candidiates left, using default move");
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
