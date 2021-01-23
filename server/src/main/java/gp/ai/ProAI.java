package gp.ai;

import gp.DamageAndPath;
import gp.NodeUtil;
import gp.Player;
import gp.model.*;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

public class ProAI extends BaseAI {

    private Map<String, PlayerState> playerMap;
    private Node location;
    private PlayerState player;
    private final Random random = new Random();
    public boolean debug;
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

        // Area indices may not be properly ordered. Cumulative value has to be calculated properly in unordered cases too.
        final Map<Integer, Node> areaToNode = new HashMap<>();
        for (Node node : nodes) {
            if (node.isPit()) continue;
            final int areaIndex = node.getAreaIndex();
            areaToNode.putIfAbsent(areaIndex, node);
        }
        for (int area = 0; area < areaToNode.size(); ++area) {
            final Node node = areaToNode.get(area);
            if (node.isCurve()) {
                cumulativeValue += AIUtil.getMinDistanceToNextCurve(node, pitLane);
            }
            areaToValue.put(node.getAreaIndex(), cumulativeValue);
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
        final int hp = enterPits ? maxHitpoints : player.getHitpoints() - damage - Math.max(0, player.getGear() - gear - 1);
        final int distance = AIUtil.getMinDistanceToNextCurve(endNode, endNode.isPit() ? Collections.emptySet() : pitLane);
        final int movePermit = AIUtil.getMaxDistanceWithoutDamage(endNode, stops, endNode.isPit() ? Collections.emptySet() : pitLane);
        return evaluate(location, endNode, hp, gear, lapsToGo, stops, distance, movePermit);
    }
    //                                 0           1  2   3   4   5   6   7   8   9   10  11  12  13  14  15  16  17  18  19  20  21  22  23  24  25  26  27  28   29   30
    private static int[] hpToScore = { Scores.MIN, 5, 10, 15, 20, 25, 30, 35, 40, 44, 48, 51, 54, 57, 60, 63, 66, 69, 72, 75, 78, 81, 84, 87, 90, 93, 96, 99, 102, 105, 108 };
    // Linear                          0           4  8   12  16  20  24  28  32  36  40  44  48  52  56  60  64  68  72

    private int evaluate(Node startNode, Node endNode, int hp, int gear, int lapsToGo, int stops, int distance, int movePermit) {
        final boolean finalStraight = !endNode.isCurve() && lapsToGo == 0 && areaToValue.get(endNode.getAreaIndex()) == cumulativeValue && hp > 1;
        //if (finalStraight) debug("Final straight!!! HP does not matter");
        // TODO: Reduce value of HP based on remaining distance
        int score = hpToScore[finalStraight ? Math.max(maxHitpoints, hp) : hp];
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
            int maxSteps = getGearAvg(Math.min(6, gear + 1));
            for (int i = 2; i <= stopsToDo; ++i) {
                maxSteps += getGearAvg(Math.min(6, gear + i));
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
            final int penalty = getPenaltyForLowGear(endNode, gear, distance, movePermit) * player.getHitpoints() / maxHitpoints;
            score -= penalty;
            description += "Penalty from low gear (pits): " + penalty + "\n";
            if (weatherForecast != null) {
                final Tires bestTires = getBestTires(tires, lapsToGo, false);
                if (bestTires != tires) {
                    score += 10;
                    description += "Bonus for switching to proper tires (pits): " + 10 + "\n";
                }
            }
            //final int penaltyForHP = Math.max(0, player.getHitpoints() - 9);
            //description += "Penalty from HP (pits): " + penaltyForHP + "\n";
            //score -= penaltyForHP;
            if (debug2) debug(description);
            return score;
        }

        if (stopsToDo <= 0) {
            final int penalty = getPenaltyForLowGear(endNode, gear, distance, movePermit);
            score -= penalty;
            description += "Penalty from low gear: " + penalty + "\n";
            stopsToDo = AIUtil.getStopsRequiredInNextCurve(endNode);
        }

        if (finalStraight) {
            return score;
        }

        final int minGear = Math.max(1, gear - Math.min(4, hp));
        int minSteps = Gear.getMin(minGear);
        for (int i = 2; i <= stopsToDo; ++i) {
            minSteps += Gear.getMin(Math.max(1, minGear - i));
        }
        if (minSteps > movePermit) {
            // Guaranteed DNF --> very bad
            return Scores.MIN;
        }

        int minStepsWithoutDamage = Gear.getAvg(Math.max(1, gear - 1));
        for (int i = 2; i <= stopsToDo; ++i) {
            minStepsWithoutDamage += Gear.getAvg(Math.max(1, gear - i));
        }
        if (minStepsWithoutDamage > movePermit) {
            // Too large gear --> take into account in evaluation
            score -= 2 * (minStepsWithoutDamage - movePermit);
            description += "Penalty from too high gear: " + (2 * (minStepsWithoutDamage - movePermit)) + "\n";
        }
        if (gear < 4) {
            int avgStepsWithoutDamage = Gear.getAvg(gear);
            for (int i = 2; i <= stopsToDo; ++i) {
                avgStepsWithoutDamage += Gear.getAvg(gear);
            }
            if (avgStepsWithoutDamage > movePermit) {
                // Too large gear --> take into account in evaluation
                score -= avgStepsWithoutDamage - movePermit;
                description += "Penalty from having to downshift: " + (avgStepsWithoutDamage - movePermit) + "\n";
            }
        }

        if (endNode.getType() == NodeType.STRAIGHT && endNode.childCount(null) == 1 && endNode.childStream().allMatch(Node::isCurve)) {
            --score;
        }
        if (debug2) debug(description);
        return score;
    }

    private int getPenaltyForLowGear(Node node, int gear, int distanceToNextCurve, int movePermit) {
        final boolean inPits = node.isPit();
        if (gear >= 5) {
            return 0;
        }
        final int minRoll = Gear.getMin(Math.min(inPits ? 4 : 6, gear + 1));
        final int penalty = Math.max(0, distanceToNextCurve - minRoll);
        if (penalty == 0 && !node.isCurve()) {
            // Approaching a corner, check that the gear is not too low for that corner.
            int maxSteps = getGearMax(Math.min(inPits ? 4 : 6, gear + 1));
            int stops = AIUtil.getStopsRequiredInNextCurve(node);
            for (int i = 2; i <= stops; ++i) {
                maxSteps = getGearMax(Math.min(inPits ? 4 : 6, gear + i));
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
        public int compareTo(@Nonnull Scores scores) {
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

    void updatePlayerInfo(GameState gameState) {
        playerMap = AIUtil.buildPlayerMap(gameState);
        player = playerMap.get(playerId);
        if (player == null) {
            throw new RuntimeException("No data sent for player: " + playerId);
        }
        location = nodes.get(player.getNodeId());
        if (location == null) {
            throw new RuntimeException("Unknown location for player: " + playerId);
        }
        tires = player.getTires();
    }

    @Override
    public gp.model.Gear selectGear(GameState gameState) {
        updatePlayerInfo(gameState);
        if (player.getGear() == 0) {
            final Tires chosenTires = getBestTires(tires, player.getLapsToGo(), true);
            if (chosenTires != tires) {
                tires = chosenTires;
                debug("Changed tires " + tires.getType().name());
            }
            gear = 1;
            return new gp.model.Gear().gear(1).tires(tires);
        }
        final int pos = gameState.getPlayers().indexOf(player) + 1;
        final List<PlayerState> competingPlayers = gameState.getPlayers().stream().filter(p -> p.getLapsToGo() >= 0 && p.getHitpoints() > 0).collect(Collectors.toList());
        final int posAmongCompeting = competingPlayers.indexOf(player) + 1;
        debug("Pos: " + pos + " Pos among running: " + posAmongCompeting);

        if (location.hasGarage()) {
            final Tires chosenTires = getBestTires(tires, player.getLapsToGo(), true);
            if (chosenTires != tires) {
                tires = chosenTires;
                debug("Changed tires " + tires.getType().name());
            }
        }
        final Set<Node> blockedNodes = playerMap
                .values()
                .stream()
                .filter(p -> p.getHitpoints() > 0 && p.getLapsToGo() >= 0)
                .map(p -> nodes.get(p.getNodeId()))
                .collect(Collectors.toSet());
        final int minGear = Math.max(1, player.getGear() - Math.min(4, player.getHitpoints()));
        final int maxGear = Math.min(location.isPit() ? 4 : 6, player.getGear() + 1);
        final Map<Integer, List<Integer>> gearToScore = new HashMap<>();
        for (int gear = minGear; gear <= maxGear; ++gear) {
            final int finalGear = gear;
            final int[] distribution = Gear.getDistribution(gear);
            for (int roll : distribution) {
                final Map<Node, DamageAndPath> res = NodeUtil.findTargetNodes(location, gear, roll, player.getHitpoints(), player.getStops(), player.getLapsToGo(), blockedNodes);
                int maxScore = res.entrySet().stream().map(e -> evaluate(e.getKey(), e.getValue().getDamage(), finalGear)).mapToInt(Integer::intValue).max().orElse(Scores.MIN);
                if (tires != null && tires.canUse()) {
                    final Map<Node, DamageAndPath> resOpt = NodeUtil.findTargetNodes(location, gear, roll + 1, player.getHitpoints(), player.getStops(), player.getLapsToGo(), blockedNodes);
                    final int optMaxScore = resOpt.entrySet().stream().map(e -> evaluate(e.getKey(), e.getValue().getDamage(), finalGear)).mapToInt(Integer::intValue).max().orElse(Scores.MIN);
                    if (optMaxScore > maxScore) {
                        maxScore = optMaxScore;
                    }
                }
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
        return new gp.model.Gear().gear(gear).tires(tires);
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

    private Tires getBestTires(Tires current, int lapsToGo, boolean free) {
        if (current == null) return null;
        int rainCount = 0;
        for (int i = 1; i < 20; ++i) {
            Weather w = getWeather(i);
            if (w == Weather.RAIN) ++rainCount;
        }
        if (rainCount > 9) {
            if (current.getType() == Tires.Type.WET) return current;
            else return new Tires(Tires.Type.WET);
        }
        if (lapsToGo <= 1) {
            if (free) {
                if (current.getType() == Tires.Type.SOFT && current.getAge() == 0) return current;
                return new Tires(Tires.Type.SOFT);
            } else {
                if (current.getType() != Tires.Type.HARD) return new Tires(Tires.Type.SOFT);
                return current;
            }
        }
        if (current.getType() == Tires.Type.WET) {
            if (random.nextInt(2) == 0) return new Tires(Tires.Type.SOFT);
            else return new Tires(Tires.Type.HARD);
        }
        if (current.getType() == Tires.Type.SOFT && current.getAge() > 0) {
            if (random.nextInt(2) == 0) return new Tires(Tires.Type.SOFT);
            else return new Tires(Tires.Type.HARD);
        }
        return current;
    }

    private int getGearAvg(int gear) {
        int avg = Gear.getAvg(gear);
        if (tires != null && tires.canUse()) {
            ++avg;
        }
        return avg;
    }

    private int getGearMax(int gear) {
        int max = Gear.getMax(gear);
        if (tires != null && tires.canUse()) {
            ++max;
        }
        return max;
    }
}
