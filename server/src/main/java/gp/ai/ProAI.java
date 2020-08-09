package gp.ai;

import gp.DamageAndPath;
import gp.NodeUtil;
import gp.Player;
import gp.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class ProAI extends BaseAI {

    private Map<String, PlayerState> playerMap;
    private Node location;
    private PlayerState player;
    private int gear;
    private final Random random = new Random();
    public boolean debug = true;
    public boolean debug2;

    private int cumulativeValue = 0;
    //private final Map<Integer, Integer> areaToStops = new HashMap<>();
    private final Map<Integer, Integer> areaToValue = new HashMap<>();
    private final Set<Node> pitLane;

    public ProAI(TrackData data) {
        super(data);
        pitLane = nodes.stream().filter(Node::isPit).collect(Collectors.toSet());
        //lapLengthInSteps = nodes.stream().filter(n -> !n.isPit()).map(Node::getStepsToFinishLine).mapToInt(Integer::intValue).max().orElse(0);
        // Compute cumulative stop counts for each area for better node evaluation
        //int cumulativeStops = 0;
        for (Node node : nodes) {
            if (node.isPit()) continue;
            final int areaIndex = node.getAreaIndex();
            /*
            if (areaToStops.get(areaIndex) == null) {
                if (node.isCurve()) {
                    cumulativeStops += node.getStopCount();
                }
                areaToStops.put(areaIndex, cumulativeStops);
            }*/
            if (areaToValue.get(areaIndex) == null) {
                if (node.isCurve()) {
                    cumulativeValue += AIUtil.getMinDistanceToNextCurve(node, pitLane);//getPenaltyForLowGear(node, 1);
                }
                areaToValue.put(areaIndex, cumulativeValue);
            }
        }
        if (!pitLane.isEmpty()) {
            final int pitLaneAreaIndex = pitLane.iterator().next().getAreaIndex();
            //areaToStops.put(pitLaneAreaIndex, cumulativeStops);
            areaToValue.put(pitLaneAreaIndex, cumulativeValue);
        }
        //System.err.println(areaToValue);
/*
        nodes.forEach(n -> {
            int bestGearScore = Integer.MIN_VALUE;
            int bestGear = 0;
            //int maxMaxScore = Integer.MIN_VALUE;
            //int lowHPScore = Integer
            final int stops = n.getStopCount();// > 0 ? 1 : 0;
            final int distance = AIUtil.getMinDistanceToNextCurve(n, n.isPit() ? Collections.emptySet() : pitLane);
            final int movePermit = AIUtil.getMaxDistanceWithoutDamage(n, stops, n.isPit() ? Collections.emptySet() : pitLane);
            for (int gear = 2; gear <= 2; ++gear) {
                final int score = evaluate(n, 18, gear, 0, stops, distance, movePermit);
                //final int maxScore = evaluate(n, 18, gear, 0, n.getStopCount());
                //final int lowHPScore = evaluate(n, 1, gear, 0, n.getStopCount());
                //System.err.println("Gear " + gear + ": " + n.getId() + ": " + score + " - " + maxScore);
                if (score > bestGearScore) {
                    bestGear = gear;
                    bestGearScore = score;
                }
            }
            System.err.println(n.getId() + ": Best gear: " + bestGear + " Score: " + bestGearScore);
        });*/
    }

    int evaluate(Node endNode, int damage, int gear) {
        final int stops = (endNode.isCurve() && endNode.getAreaIndex() != location.getAreaIndex()) ? 1 : player.getStops() + 1;
        int lapsToGo = player.getLapsToGo();
        if (!endNode.isPit()) {
            if (location.isPit() || location.getAreaIndex() > endNode.getAreaIndex()) --lapsToGo;
        }
        final boolean enterPits = endNode.hasGarage() || (endNode.isPit() && !location.isPit());
        final int hp = enterPits ? 18 : player.getHitpoints() - damage - Math.max(0, player.getGear() - gear - 1);
        final int distance = AIUtil.getMinDistanceToNextCurve(endNode, endNode.isPit() ? Collections.emptySet() : pitLane);
        final int movePermit = AIUtil.getMaxDistanceWithoutDamage(endNode, stops, endNode.isPit() ? Collections.emptySet() : pitLane);
        return evaluate(location, endNode, hp, gear, lapsToGo, stops, distance, movePermit);
    }

    private int evaluate(Node startNode, Node endNode, int hp, int gear, int lapsToGo, int stops, int distance, int movePermit) {
        // TODO: Non-linear evaulation of hitpoints
        final boolean finalStraight = !endNode.isCurve() && lapsToGo == 0 && areaToValue.get(endNode.getAreaIndex()) == cumulativeValue && hp > 1;
        //if (finalStraight) debug("Final straight!!! HP does not matter");
        int score = 3 * (finalStraight ? Math.min(18, hp) : hp);
        String description = "Score from HP: " + score + "\n";

        if (lapsToGo < 0) {
            return Scores.MAX + score; // Prefer finishing without wasting hitpoints
        }

        // Take number of steps to finish line into account
        //final int steps = node.getStepsToFinishLine();
        //score -= lapsToGo * lapLengthInSteps + steps;
        final int requiredStopCount = endNode.isCurve() ? endNode.getStopCount() : startNode.getStopCount();
        int stopsToDo = Math.max(0, requiredStopCount - stops);

        // Each curve accumulates value depending on the following straight length
        if (requiredStopCount > 1 && stopsToDo > 0) {
            final int previousValue = areaToValue.get(endNode.getAreaIndex() - 1);
            final int value = areaToValue.get(endNode.getAreaIndex());
            final int stopValue = (value - previousValue) / (requiredStopCount - 1);
            score -= stopsToDo * stopValue;
            description += "Penalty from missing stops in the curve: " + (stopsToDo * stopValue) + "\n";

            // Penatly from too low gear...
            int maxSteps = Gear.getAvg(Math.min(6, gear + 1));
            for (int i = 2; i <= stopsToDo; ++i) {
                maxSteps += Gear.getAvg(Math.min(6, gear + i));
            }
            if (movePermit > maxSteps) {
                score -= movePermit - maxSteps;
                description += "Penalty from low gear: " + (movePermit - maxSteps) + "\n";
            }
        } else {
            score -= distance;
            description += "Penalty from distance: " + distance + "\n";
        }
        final int value = 2 * (areaToValue.get(endNode.getAreaIndex()) - lapsToGo * cumulativeValue);
        score += value;
        description += "Score from cumulative area value: " + value + "\n";

        if (endNode.isPit()) {
            score -= getPenaltyForLowGear(endNode, gear, distance, movePermit);
            //description += "Penalty from low gear (pits): " + getPenaltyForLowGear(node, gear, distance, movePermit) + "\n";
            if (debug2) debug(description);
            return score;
        }

        if (stopsToDo <= 0) {
            final int penalty = getPenaltyForLowGear(endNode, gear, distance, movePermit);
            score -= penalty;
            description += "Penalty from low gear: " + penalty + "\n";
            stopsToDo = AIUtil.getStopsRequiredInNextCurve(endNode);
        }

        if (stopsToDo > 1) {
            final int minGear = Math.max(1, gear - Math.min(4, hp - 1));
            int minSteps = Gear.getMin(minGear);
            for (int i = 2; i <= stopsToDo; ++i) {
                minSteps += Gear.getMin(Math.max(1, minGear - i));
            }
            if (minSteps > movePermit) {
                // Guaranteed DNF --> very bad
                return Scores.MIN;
            }
        }

        int minStepsWithoutDamage = Gear.getAvg(Math.max(1, gear - 1));
        for (int i = 2; i <= stopsToDo; ++i) {
            minStepsWithoutDamage += Gear.getAvg(Math.max(1, gear - i));
        }
        if (minStepsWithoutDamage > movePermit) {
            // Too large gear --> take into account in evaluation
            score -= 3 * (minStepsWithoutDamage - movePermit);
            description += "Penalty from high gear: " + (3 * (minStepsWithoutDamage - movePermit)) + "\n";
        }
        if (debug2) debug(description);
        return score;
    }

    private int getPenaltyForLowGear(Node node, int gear, int distanceToNextCurve, int movePermit) {
        final boolean inPits = node.isPit();
        if (gear >= (inPits ? 3 : 5)) {
            return 0;
        }
        final int minRoll = Gear.getMin(Math.min(inPits ? 4 : 6, gear + 1));
        final int penalty = Math.max(0, distanceToNextCurve - minRoll);
        if (penalty == 0 && !node.isCurve()) {
            // Approaching a corner, check that the gear is not too low for that corner.
            int maxSteps = Gear.getMax(Math.min(inPits ? 4 : 6, gear + 1));
            int stops = AIUtil.getStopsRequiredInNextCurve(node);
            for (int i = 2; i <= stops; ++i) {
                maxSteps = Gear.getMax(Math.min(inPits ? 4 : 6, gear + i));
            }
            return Math.max(0, movePermit - maxSteps);
        }
        return penalty;
        /*
        final int distanceToNextCurve = AIUtil.getMinDistanceToNextCurve(node, inPits ? Collections.emptySet() : pitLane);
        final int minRoll = Gear.getMin(inPits ? 4 : 6);
        final int maxRoll = Gear.getMax(inPits ? 4 : 6);
        final int maxTurnsNeeded = (distanceToNextCurve + minRoll - 1) / minRoll;
        final int minTurnsNeeded = (distanceToNextCurve + maxRoll - 1) / maxRoll;
        int minGear = Math.min(inPits ? 4 : 6, gear + 1);
        int maxGear = Math.min(inPits ? 4 : 6, gear + 1);
        int minTurnsSpent = 1;
        int maxTurnsSpent = 1;
        int minCoveredDistance = Gear.getMin(minGear);
        int maxCoveredDistance = Gear.getMax(maxGear);
        int distanceToPitExit = 0;
        if (inPits) {
            Node pitNode = node;
            while (pitNode != null) {
                pitNode = pitNode.childStream().filter(Node::isPit).findAny().orElse(null);
                ++distanceToPitExit;
            }
        }
        while (minCoveredDistance < distanceToNextCurve) {
            minGear = Math.min(minCoveredDistance < distanceToPitExit ? 4 : 6, minGear + 1);
            minCoveredDistance += Gear.getMin(maxGear);
            ++minTurnsSpent;
            if (maxCoveredDistance < distanceToNextCurve) {
                maxCoveredDistance += Gear.getMax(maxGear);
                maxGear = Math.min(maxCoveredDistance < distanceToPitExit ? 4 : 6, maxGear + 1);
                ++maxTurnsSpent;
            }
        }
        final int penaltyPerTurn = 10;
        final double avgTurnsNeeded = (minTurnsNeeded + maxTurnsNeeded) / 2.0;
        final double avgTurnsSpent = (minTurnsSpent + maxTurnsSpent) / 2.0;
        //System.err.println(distanceToNextCurve + " -- " + avgTurnsNeeded + " " + avgTurnsSpent);
        return (int) (penaltyPerTurn * (avgTurnsSpent - avgTurnsNeeded));
        */
    }

    private static class Scores implements Comparable<Scores> {
        private final static int MIN = -1000000;
        private final static int MAX = 1000000;
        final int gear;
        final int min;
        final int median;
        final int max;
        Scores(int gear, int min, int median, int max) {
            this.gear = gear;
            this.min = min;
            this.median = median;
            this.max = max;
        }
        @Override
        public int compareTo(Scores scores) {
            final int sum1 = median + min + max;
            final int sum2 = scores.median + scores.min + scores.max;
            if (sum2 == sum1) {
                if (scores.median == median) {
                    if (scores.max == max) {
                        if (scores.min == min) {
                            return scores.gear - gear;
                        }
                        return scores.min - min;
                    }
                    return scores.max - max;
                }
                return scores.median - median;
            }
            return sum2 - sum1;
        }
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
        final int pos = gameState.getPlayers().indexOf(player) + 1;
        final List<PlayerState> competingPlayers = gameState.getPlayers().stream().filter(p -> p.getLapsToGo() >= 0 && p.getHitpoints() > 0).collect(Collectors.toList());
        final int posAmongCompeting = competingPlayers.indexOf(player) + 1;
        debug("Pos: " + pos + " Pos among running: " + posAmongCompeting);

        final Set<Node> blockedNodes = playerMap
                .values()
                .stream()
                .filter(p -> p.getHitpoints() > 0 && p.getLapsToGo() >= 0)
                .map(p -> nodes.get(p.getNodeId()))
                .collect(Collectors.toSet());
        final int minGear = Math.max(1, player.getGear() - Math.min(4, player.getHitpoints() - 1));
        final int maxGear = Math.min(location.isPit() ? 4 : 6, player.getGear() + 1);
        final Map<Integer, List<Integer>> gearToScore = new HashMap<>();
        for (int gear = minGear; gear <= maxGear; ++gear) {
            final int finalGear = gear;
            final int[] distribution = Gear.getDistribution(gear);
            for (int roll : distribution) {
                final Map<Node, DamageAndPath> res = NodeUtil.findTargetNodes(location, gear, roll, player.getHitpoints(), player.getStops(), player.getLapsToGo(), blockedNodes);
                final int maxScore = res.entrySet().stream().map(e -> evaluate(e.getKey(), e.getValue().getDamage(), finalGear)).mapToInt(Integer::intValue).max().orElse(Scores.MIN);
                gearToScore.computeIfAbsent(gear, g -> new ArrayList<>()).add(maxScore);
            }
        }
        // Safe option: Pick gear with largest worst score.
        // Normal option: Pick gear with largest median scoree.
        // Risky option: Pick gear with largest best score.
        // TODO: Choose risk based on position and maybe HP
        final List<Scores> results = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> e : gearToScore.entrySet()) {
            final List<Integer> scores = e.getValue();
            if (scores.isEmpty()) continue;
            scores.sort(Integer::compareTo);
            final int minScore = scores.get(0);
            final int medianScore = scores.get(scores.size() / 2);
            final int maxScore = scores.get(scores.size() - 1);
            debug("Gear " + e.getKey() + ": " + minScore + " - " + medianScore + " - " + maxScore);
            results.add(new Scores(e.getKey(), minScore, medianScore, maxScore));
        }
        results.sort(Scores::compareTo);
        gear = results.get(0).gear;
        debug("Chose gear " + gear);
        return new gp.model.Gear().gear(gear);
    }

    @Override
    public SelectedIndex selectMove(Moves allMoves) {
        if (allMoves.getMoves().isEmpty()) {
            throw new RuntimeException("No valid targets provided by server!");
        }
        final List<ValidMove> moves = allMoves.getMoves();
        final List<Integer> bestIndices = new ArrayList<>();
        int maxScore = Integer.MIN_VALUE;
        for (int i = 0; i < moves.size(); ++i) {
            final ValidMove vm = moves.get(i);
            final Node endNode = nodes.get(vm.getNodeId());
            final int damage = vm.getBraking() + vm.getOvershoot();
            final int score = evaluate(endNode, damage, gear);
            if (score > maxScore) {
                maxScore = score;
                bestIndices.clear();
            }
            if (score >= maxScore) {
                bestIndices.add(i);
            }
        }
        return new SelectedIndex().index(bestIndices.get(random.nextInt(bestIndices.size())));
    }

    private void debug(String msg) {
        if (debug) {
            System.err.println(msg);
        }
    }
}