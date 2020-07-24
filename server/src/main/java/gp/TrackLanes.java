package gp;

import gp.ai.Node;

import java.util.*;
import java.util.stream.Collectors;

public class TrackLanes {
    private static class Lane {
        private final List<Node> nodes = new ArrayList<>();
        private final Map<Node, Double> distanceMap;
        private final Map<Node, Set<Node>> collisionMap;

        private Lane(Node start, Map<Node, Double> distanceMap, Map<Node, Set<Node>>  collisionMap) {
            nodes.add(start);
            this.distanceMap = distanceMap;
            this.collisionMap = collisionMap;
        }

        private Node getLastNode() {
            return nodes.get(nodes.size() - 1);
        }

        private double getDistance() {
            return distanceMap.get(getLastNode());
        }

        private boolean canContinueTo(Node node) {
            return getLastNode().hasChild(node);
        }

        private void addNode(Node node) {
            collisionMap.computeIfAbsent(getLastNode(), _node -> new HashSet<>()).add(node);
            nodes.add(node);
        }

        private void complete() {
            collisionMap.computeIfAbsent(getLastNode(), _node ->  new HashSet<>()).add(nodes.get(0));
        }

        private void checkCollisions(Lane otherLane) {
            for (int i = 1; i < nodes.size(); ++i) {
                final Node prev = nodes.get(i - 1);
                final Node node = nodes.get(i);
                final double distPrev = distanceMap.get(prev);
                final double dist = distanceMap.get(node);
                for (Node otherNode : otherLane.nodes) {
                    final double distOther = distanceMap.get(otherNode);
                    if (distOther <= distPrev) continue;
                    collisionMap.get(node).add(otherNode);
                    collisionMap.get(otherNode).add(node);
                    if (distOther >= dist) break;
                }
            }
            final Node firstNode = nodes.get(0);
            if (distanceMap.get(firstNode) == 0) {
                collisionMap.get(firstNode).add(otherLane.getLastNode());
                collisionMap.get(otherLane.getLastNode()).add(firstNode);
            }
            collisionMap.get(firstNode).add(otherLane.nodes.get(0));
            collisionMap.get(otherLane.nodes.get(0)).add(firstNode);
        }
    }

    private static int distanceToInt(double distance) {
        return (int) (100 * distance + 0.5);
    }

    public static Map<Node, Set<Node>> buildCollisionMap(Collection<Node> nodes, Map<Node, Double> distanceMap) {
        final Map<Node, Set<Node>> collisionMap = new HashMap<>();
        List<Node> sortedNodes = nodes
                .stream()
                .filter(n -> n.getType() != Node.Type.PIT)
                .sorted(Comparator.comparingInt(n1 -> distanceToInt(distanceMap.get(n1))))
                .collect(Collectors.toList());
        final List<Node> finishLine = sortedNodes.subList(0, 3);
        if (finishLine.stream().anyMatch(n -> n.getType() != Node.Type.FINISH)) {
            throw new RuntimeException("Finish line nodes don't have the lowest distance");
        }
        // Middle node is either 1st node (distance 0.0) or 3rd node (distance 0.5)
        final int middleIndex = distanceMap.get(finishLine.get(0)).equals(distanceMap.get(finishLine.get(1))) ? 2 : 0;
        final Lane[] lanes = new Lane[3];
        lanes[0] = new Lane(finishLine.get(middleIndex == 2 ? 0 : 1), distanceMap, collisionMap);
        lanes[1] = new Lane(finishLine.get(middleIndex), distanceMap, collisionMap);
        lanes[2] = new Lane(finishLine.get(middleIndex == 2 ? 1 : 2), distanceMap, collisionMap);

        sortedNodes = sortedNodes.subList(3, sortedNodes.size());
        sortedNodes.forEach(node -> {
            final Lane matchingLane = Arrays
                    .stream(lanes)
                    .filter(lane -> lane.canContinueTo(node))
                    .min(Comparator.comparingInt(l -> distanceToInt(l.getDistance())))
                    .orElse(null);
            if (matchingLane == null) {
                throw new RuntimeException("Internal error when calculating lanes");
            }
            matchingLane.addNode(node);
        });
        Arrays.stream(lanes).forEach(Lane::complete);

        // Now collision map contains all nodes and it is built for each lane. Next we will figure out
        // possible collisions between lanes. Collisions between lanes are symmetric so we only need to check
        // edge lanes against the middle lane.
        lanes[0].checkCollisions(lanes[1]);
        lanes[2].checkCollisions(lanes[1]);

        return collisionMap;
    }
}
