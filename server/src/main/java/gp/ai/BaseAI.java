package gp.ai;

import gp.Main;
import gp.model.*;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public abstract class BaseAI implements AI {

    String playerId;
    final TrackData data;
    final List<Node> nodes;
    private final int lapLengthInSteps;
    final Set<Node> pitLane;

    public BaseAI(TrackData data) {
        this.data = data;
        nodes = data.getNodes();
        pitLane = nodes.stream().filter(Node::isPit).collect(Collectors.toSet());
        lapLengthInSteps = nodes.stream().filter(n -> !n.isPit()).map(Node::getStepsToFinishLine).mapToInt(Integer::intValue).max().orElse(0);
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

    int evaluate(PlayerState playerState) {
        final Node node = nodes.get(playerState.getNodeId());
        final int hp = playerState.getHitpoints();
        final int gear = playerState.getGear();
        final int lapsToGo = playerState.getLapsToGo();
        final int stops = playerState.getStops();
        return evaluate(node, hp, gear, lapsToGo, stops);
    }

    // Evaluation results should only be compared within 1 area!!!
    int evaluate(Node node, int hp, int gear, int lapsToGo, int stops) {
        final int steps = node.getStepsToFinishLine();

        int score = 2 * hp;
        score -= lapsToGo * lapLengthInSteps + steps;

        int stopsToDo = node.getStopCount() - stops;
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
            return Integer.MIN_VALUE;
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
}
