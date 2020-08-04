package gp.ai;

import gp.DamageAndPath;
import gp.NodeUtil;
import gp.model.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class BestAI extends BaseAI {

    private Map<String, PlayerState> playerMap;
    private Node location;
    private PlayerState player;
    private int gear;
    private final Random random = new Random();
    public boolean debug = true;

    private final int lapLengthInSteps;
    private int cumulativeStops = 0;
    private final Map<Integer, Integer> areaToStops = new HashMap<>();
    private final Set<Node> pitLane;

    public BestAI(TrackData data) {
        super(data);
        pitLane = nodes.stream().filter(Node::isPit).collect(Collectors.toSet());
        lapLengthInSteps = nodes.stream().filter(n -> !n.isPit()).map(Node::getStepsToFinishLine).mapToInt(Integer::intValue).max().orElse(0);
        // Compute cumulative stop counts for each area for better node evaluation
        for (Node node : nodes) {
            if (node.isPit()) continue;
            if (areaToStops.get(node.getAreaIndex()) == null) {
                if (node.isCurve()) {
                    cumulativeStops += node.getStopCount();
                }
                areaToStops.put(node.getAreaIndex(), cumulativeStops);
            }
        }
        if (!pitLane.isEmpty()) {
            areaToStops.put(pitLane.iterator().next().getAreaIndex(), cumulativeStops);
        }
    }

    private int evaluate(Node endNode, int damage) {
        final int stops = endNode.isCurve() ? (endNode.getAreaIndex() == location.getAreaIndex() ? player.getStops() + 1 : 1) : 0;
        int lapsToGo = player.getLapsToGo();
        if (!endNode.isPit()) {
            if (location.isPit() || location.getAreaIndex() > endNode.getAreaIndex()) --lapsToGo;
        }
        final boolean enterPits = endNode.hasGarage() || (endNode.isPit() && !location.isPit());
        final int hp = enterPits ? 18 : player.getHitpoints() - damage;
        return evaluate(endNode, hp, gear, lapsToGo, stops);
    }

    private int evaluate(PlayerState playerState) {
        final Node node = nodes.get(playerState.getNodeId());
        final int hp = playerState.getHitpoints();
        final int gear = playerState.getGear();
        final int lapsToGo = playerState.getLapsToGo();
        final int stops = playerState.getStops();
        return evaluate(node, hp, gear, lapsToGo, stops);
    }

    private int evaluate(Node node, int hp, int gear, int lapsToGo, int stops) {
        if (lapsToGo < 0) {
            return 1000000;
        }
        final int steps = node.getStepsToFinishLine();

        int score = 2 * hp;
        //System.err.println("Laps to go: " + lapsToGo + " steps needed: " + steps);
        score -= lapsToGo * lapLengthInSteps + steps;
        //System.err.println("Score after move step reductions: " + score);

        int stopsToDo = Math.max(0, node.getStopCount() - stops);
        score -= (lapsToGo * cumulativeStops - areaToStops.get(node.getAreaIndex()) + stopsToDo) * 10; // value of each stop is 10
        //System.err.println("Score after cumulative stop reductions: " + score);

        if (node.isPit()) {
            final int distanceToNextCurve = AIUtil.getMinDistanceToNextCurve(node, Collections.emptySet());
            final int maxSteps = Gear.getMax(Math.min(4, gear + 1));
            if (maxSteps < distanceToNextCurve) {
                // Too small gear --> take into account in evaluation
                score -= distanceToNextCurve - maxSteps;
            }
            return score;
        }

        if (stopsToDo <= 0) {
            final int distanceToNextCurve = AIUtil.getMinDistanceToNextCurve(node, pitLane);
            final int maxSteps = Gear.getMax(Math.min(6, gear + 1));
            if (maxSteps < distanceToNextCurve) {
                // Too small gear --> take into account in evaluation
                score -= distanceToNextCurve - maxSteps;
                //System.err.println("Penalty from too small gear: " + (distanceToNextCurve - maxSteps));
            }
            stopsToDo = AIUtil.getStopsRequiredInNextCurve(node);
        }
        final int movePermit = AIUtil.getMaxDistanceWithoutDamage(node, stops, pitLane);

        final int minGear = Math.max(1, gear - Math.min(4, hp - 1));
        int minSteps = Gear.getMin(minGear);
        for (int i = 2; i <= stopsToDo; ++i) {
            minSteps += Gear.getMin(Math.max(1, minGear - i));
        }
        if (minSteps > movePermit) {
            // Guaranteed DNF --> very bad
            //System.err.println("Penalty from DNF");
            return -1000000;
        }

        int minStepsWithoutDamage = Gear.getMin(Math.max(1, gear - 1));
        for (int i = 2; i <= stopsToDo; ++i) {
            minStepsWithoutDamage += Gear.getMin(Math.max(1, gear - i));
        }
        if (minStepsWithoutDamage > movePermit) {
            // Too large gear --> take into account in evaluation
            score -= minStepsWithoutDamage - movePermit;
            //System.err.println("Penalty from too large gear: " + (minStepsWithoutDamage - movePermit));
        }
        return score;
    }

    private static class Scores implements Comparable<Scores> {
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
        final int minGear = Math.max(1, player.getGear() - Math.min(4, player.getHitpoints() - 1));
        final int maxGear = Math.min(location.isPit() ? 4 : 6, player.getGear() + 1);
        final Map<Integer, List<Integer>> gearToScore = new HashMap<>();
        for (int gear = minGear; gear <= maxGear; ++gear) {
            final int[] distribution = Gear.getDistribution(gear);
            for (int roll : distribution) {
                final Map<Node, DamageAndPath> res = NodeUtil.findTargetNodes(location, gear, roll, player.getHitpoints(), player.getStops(), player.getLapsToGo(), blockedNodes);
                final int maxScore = res.entrySet().stream().map(e -> evaluate(e.getKey(), e.getValue().getDamage())).mapToInt(Integer::intValue).max().orElse(-1000000);
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
            final int score = evaluate(endNode, damage);
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
