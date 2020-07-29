package gp;

import gp.ai.Node;
import gp.ai.NodeType;

import java.util.*;
import java.util.stream.Collectors;

public class TrackLanes {
    private static class Lane {
        private final List<Node> nodes = new ArrayList<>();
        private final Map<Node, Set<Node>> collisionMap;

        private Lane(Node start, Map<Node, Set<Node>> collisionMap) {
            nodes.add(start);
            this.collisionMap = collisionMap;
        }

        private Node getLastNode() {
            return nodes.get(nodes.size() - 1);
        }

        private double getDistance() {
            return getLastNode().getDistance();
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
                final double distPrev = prev.getDistance();
                final double dist = node.getDistance();
                for (Node otherNode : otherLane.nodes) {
                    final double distOther = otherNode.getDistance();
                    if (distOther <= distPrev) continue;
                    collisionMap.get(node).add(otherNode);
                    collisionMap.get(otherNode).add(node);
                    if (distOther >= dist) break;
                }
            }
            final Node firstNode = nodes.get(0);
            if (firstNode.getDistance() == 0) {
                collisionMap.get(firstNode).add(otherLane.getLastNode());
                collisionMap.get(otherLane.getLastNode()).add(firstNode);
            }
            collisionMap.get(firstNode).add(otherLane.nodes.get(0));
            collisionMap.get(otherLane.nodes.get(0)).add(firstNode);
        }

        private void checkCurveDistances(Lane lane) {
            boolean inCurve = false;
            int i = 0;
            int j = 0;
            while (i + 1 < nodes.size() && j + 1 < lane.nodes.size()) {
                final boolean nextIsCurve1 = nodes.get(i + 1).isCurve();
                final boolean nextIsCurve2 = lane.nodes.get(j + 1).isCurve();
                if (nextIsCurve1 != inCurve && nextIsCurve2 != inCurve) {
                    final double dist1 = nodes.get(i).getDistance();
                    final double dist2 = lane.nodes.get(j).getDistance();
                    final Node curveTransition1 = nodes.get(dist1 > dist2 ? i : i + 1);
                    final Node curveTransition2 = lane.nodes.get(dist1 > dist2 ? j + 1 : j);
                    if (curveTransition1.getDistance() != curveTransition2.getDistance()) {
                        throw new RuntimeException("Distances not properly configured at curve: " + curveTransition1.getId() + ", " + curveTransition2.getId());
                    }
                }
                if (nextIsCurve1 != inCurve && nextIsCurve2 != inCurve) {
                    inCurve = !inCurve;
                    ++i;
                    ++j;
                } else {
                    if (nextIsCurve1 == inCurve) {
                        ++i;
                    }
                    if (nextIsCurve2 == inCurve) {
                        ++j;
                    }
                }
            }
        }
    }

    public static int distanceToInt(double distance) {
        return (int) (100 * distance + 0.5);
    }

    public static Map<Node, Set<Node>> buildCollisionMap(Collection<Node> nodes) {
        if (nodes.stream().anyMatch(node -> node.getDistance() < 0.0)) {
            throw new RuntimeException("Some nodes have undefined distances when trying to build collision map");
        }
        final Map<Node, Set<Node>> collisionMap = new HashMap<>();
        List<Node> sortedNodes = nodes
                .stream()
                .filter(n -> n.getType() != NodeType.PIT)
                .sorted(Comparator.comparingInt(n1 -> distanceToInt(n1.getDistance())))
                .collect(Collectors.toList());
        final List<Node> finishLine = sortedNodes.subList(0, 3);
        if (finishLine.stream().anyMatch(n -> n.getType() != NodeType.FINISH)) {
            throw new RuntimeException("Finish line nodes don't have the lowest distance");
        }
        // Middle node is either 1st node (distance 0.0) or 3rd node (distance 0.5)
        final int middleIndex = finishLine.get(0).getDistance() == finishLine.get(1).getDistance() ? 2 : 0;
        final Lane[] lanes = new Lane[3];
        lanes[0] = new Lane(finishLine.get(middleIndex == 2 ? 0 : 1), collisionMap);
        lanes[1] = new Lane(finishLine.get(middleIndex), collisionMap);
        lanes[2] = new Lane(finishLine.get(middleIndex == 2 ? 1 : 2), collisionMap);

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

        // Add collisions for pit exit
        nodes.stream().filter(node -> node.getType() == NodeType.PIT).forEach(node -> {
            collisionMap.put(node, new HashSet<>());
            node.forEachChild(next -> {
                if (next.getType() != NodeType.PIT) {
                    collisionMap.get(node).add(next);
                    collisionMap.get(next).add(node);
                }
            });
        });

        // Add collisions for pit entry
        sortedNodes.forEach(node -> {
            node.forEachChild(next -> {
                if (next.getType() == NodeType.PIT) {
                    collisionMap.get(node).add(next);
                    collisionMap.get(next).add(node);
                }
            });
        });

        // Sanity checks for distancee definitions
        lanes[0].checkCurveDistances(lanes[2]);

        return collisionMap;
    }
}
