package gp;

import gp.ai.Node;
import gp.ai.NodeType;

import java.util.*;
import java.util.stream.Collectors;

public class TrackLanes {
    static class Lane {
        private final List<Node> nodes = new ArrayList<>();
        private final Map<Node, Set<Node>> collisionMap;

        Lane(Node start, Map<Node, Set<Node>> collisionMap) {
            nodes.add(start);
            this.collisionMap = collisionMap;
        }

        private Node getLastNode() {
            return nodes.get(nodes.size() - 1);
        }

        double getDistance() {
            return getLastNode().getDistance();
        }

        List<Node> getNodes() {
            return nodes;
        }

        boolean canContinueTo(Node node) {
            return getLastNode().hasChild(node);
        }

        void addNode(Node node) {
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

        private void checkCurveDistances(Lane lane, int laneCount) {
            boolean inCurve = false;
            int i = 0;
            int j = 0;
            while (i + 1 < nodes.size() && j + 1 < lane.nodes.size()) {
                if (nodes.get(i + 1).getType() == NodeType.BLOCKED) {
                    final double targetDistance = nodes.get(i + 1).getDistance();
                    while (j + 1 < lane.nodes.size() && lane.nodes.get(j + 1).getDistance() < targetDistance) {
                        ++j;
                    }
                    ++i;
                    continue;
                }
                if (lane.nodes.get(j + 1).getType() == NodeType.BLOCKED) {
                    final double targetDistance = lane.nodes.get(j + 1).getDistance();
                    while (i + 1 < nodes.size() && nodes.get(i + 1).getDistance() < targetDistance) {
                        ++i;
                    }
                    ++j;
                    continue;
                }
                final boolean nextIsCurve1 = nodes.get(i + 1).isCurve();
                final boolean nextIsCurve2 = lane.nodes.get(j + 1).isCurve();
                if (nextIsCurve1 != inCurve && nextIsCurve2 != inCurve) {
                    final double dist1 = nodes.get(i).getDistance();
                    final double dist2 = lane.nodes.get(j).getDistance();
                    final Node curveTransition1 = nodes.get(dist1 > dist2 ? i : i + 1);
                    final Node curveTransition2 = lane.nodes.get(dist1 > dist2 ? j + 1 : j);
                    final double distanceDiff = (laneCount - 3) * (dist1 > dist2 ? -0.5 : 0.5);
                    if (curveTransition1.getDistance() + distanceDiff != curveTransition2.getDistance()) {
                        // Allow the case where the first node on the straight is in the middle
                        if (nodes.get(i).getDistance() != lane.nodes.get(j).getDistance()) {
                            throw new RuntimeException("Distances not properly configured at curve: " + curveTransition1.getId() + ", " + curveTransition2.getId());
                        }
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

    public static Map<Node, Set<Node>> buildCollisionMap(Collection<Node> nodes, int laneCount) {
        if (nodes.stream().anyMatch(node -> node.getDistance() < 0.0)) {
            throw new RuntimeException("Some nodes have undefined distances when trying to build collision map");
        }
        final Map<Node, Set<Node>> collisionMap = new HashMap<>();
        List<Node> sortedNodes = nodes
                .stream()
                .filter(n -> n.getType() != NodeType.PIT)
                .sorted(Comparator.comparingInt(n1 -> distanceToInt(n1.getDistance())))
                .collect(Collectors.toList());
        final Lane[] lanes = initLanes(laneCount, sortedNodes, collisionMap);
        sortedNodes = sortedNodes.subList(laneCount, sortedNodes.size());
        sortedNodes.forEach(node -> {
            final Lane matchingLane = Arrays
                    .stream(lanes)
                    .filter(lane -> lane.canContinueTo(node))
                    .min(Comparator.comparingInt(l -> distanceToInt(l.getDistance())))
                    .orElse(null);
            if (matchingLane == null) {
                throw new RuntimeException("Internal error when calculating lanes: " + node.getId());
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
        sortedNodes.forEach(node -> node.forEachChild(next -> {
            if (next.getType() == NodeType.PIT) {
                collisionMap.get(node).add(next);
                collisionMap.get(next).add(node);
            }
        }));

        // Sanity checks for distance definitions
        lanes[0].checkCurveDistances(lanes[laneCount - 1], laneCount);

        return collisionMap;
    }

    static Lane[] initLanes(int laneCount, List<Node> sortedNodes, Map<Node, Set<Node>> collisionMap) {
        final List<Node> finishLine = sortedNodes.subList(0, laneCount);
        if (finishLine.stream().anyMatch(n -> !n.hasFinish())) {
            throw new RuntimeException("Finish line nodes don't have the lowest distance");
        }
        final Lane[] lanes = new Lane[laneCount];
        if (laneCount == 3) {
            final int middleIndex = finishLine.get(0).getDistance() == finishLine.get(1).getDistance() ? 2 : 0;
            lanes[0] = new Lane(finishLine.get(middleIndex == 2 ? 0 : 1), collisionMap);
            lanes[1] = new Lane(finishLine.get(middleIndex), collisionMap);
            lanes[2] = new Lane(finishLine.get(middleIndex == 2 ? 1 : 2), collisionMap);
        } else {
            final Node node1 = finishLine.get(0);
            final Node node2 = finishLine.get(1);
            if (node1.hasChild(finishLine.get(2)) && node1.hasChild(finishLine.get(3))) {
                lanes[0] = new TrackLanes.Lane(node2, collisionMap);
                final Node commonChild = finishLine.stream().filter(n -> node1.hasChild(n) && node2.hasChild(n)).findAny().orElse(null);
                if (commonChild == null) {
                    throw new RuntimeException("Malformed finish line");
                }
                lanes[1] = new TrackLanes.Lane(commonChild, collisionMap);
                lanes[2] = new TrackLanes.Lane(node1, collisionMap);
                lanes[3] = new TrackLanes.Lane(commonChild == finishLine.get(2) ? finishLine.get(3) : finishLine.get(2), collisionMap);
            } else if (node2.hasChild(finishLine.get(2)) && node2.hasChild(finishLine.get(3))) {
                lanes[0] = new TrackLanes.Lane(node1, collisionMap);
                final Node commonChild = finishLine.stream().filter(n -> node1.hasChild(n) && node2.hasChild(n)).findAny().orElse(null);
                if (commonChild == null) {
                    throw new RuntimeException("Malformed finish line");
                }
                lanes[1] = new TrackLanes.Lane(commonChild, collisionMap);
                lanes[2] = new TrackLanes.Lane(node2, collisionMap);
                lanes[3] = new TrackLanes.Lane(commonChild == finishLine.get(2) ? finishLine.get(3) : finishLine.get(2), collisionMap);
            } else {
                throw new RuntimeException("Malformed finish line");
            }
        }
        return lanes;
    }
}
