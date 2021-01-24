package gp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gp.ai.Node;
import gp.ai.NodeType;

public abstract class NodeUtil {
    // Finds path from initial node to the given node at given distance
    private static List<Node> findPath(Node node,
                                       int distance,
                                       Map<Integer, Set<Node>> distanceMap) {
        final List<Node> path = new ArrayList<>();
        path.add(node);
        for (int i = distance - 1; i >= 0; i--) {
            for (Node previous : distanceMap.get(i)) {
                if (previous.hasChild(path.get(0))) {
                    path.add(0, previous);
                    break;
                }
            }
        }
        if (path.size() != distance + 1) {
            throw new RuntimeException("Unable to find path");
        }
        return path;
    }

    private static void addWork(Node node,
                                int currentDistance,
                                Map<Integer, Set<Node>> distanceMap) {
        distanceMap.computeIfAbsent(currentDistance + 1, _node -> new HashSet<>()).add(node);
    }

    private static Map<Node, DamageAndPath> findNodes(Node startNode,
                                                      int targetDistance,
                                                      Set<Node> forbiddenNodes,
                                                      boolean allowCurveEntry,
                                                      int stopsDone,
                                                      boolean finalLap,
                                                      boolean allowPitEntry) {
        // Set of visited non-curve nodes, for finding the shortest path in straights
        final Set<Node> visited = new HashSet<>();
        // For each node which is at target distance, calculate the damage and path
        final Map<Node, DamageAndPath> result = new HashMap<>();
        // Collects all nodes at certain distances, used for finding paths
        final Map<Integer, Set<Node>> distanceMap = new HashMap<>();
        addWork(startNode, -1, distanceMap);
        if (!startNode.isCurve()) {
            visited.add(startNode);
        }
        for (int distance = 0; distance <= targetDistance && distanceMap.containsKey(distance); distance++) {
            for (Node node : distanceMap.get(distance)) {
                if (distance == targetDistance) {
                    result.put(node, new DamageAndPath(0, findPath(node, distance, distanceMap)));
                    continue;
                }
                if (finalLap && !startNode.hasFinish() && node.hasFinish()) {
                    result.put(node, new DamageAndPath(0, findPath(node, distance, distanceMap)));
                    continue;
                }
                final int finalDistance = distance;
                node.forEachChild(next -> {
                    if (forbiddenNodes.contains(next)) {
                        // node is blocked
                        return;
                    }
                    if (!allowPitEntry && next.getType() == NodeType.PIT) {
                        // cannot enter pits on final lap or with too large gear
                        return;
                    }
                    if (node.isCurve() || next.isCurve()) {
                        if (!node.isCurve()) {
                            // entering curve
                            if (allowCurveEntry) {
                                addWork(next, finalDistance, distanceMap);
                            }
                        } else if (!next.isCurve()) {
                            // exiting curve
                            final int stopsToDo = node.getStopCount() - stopsDone;
                            if (stopsToDo <= 1) {
                                final boolean allowEntry = stopsToDo <= 0;
                                final int damage = stopsToDo <= 0 ? 0 : targetDistance - finalDistance;
                                final List<Node> path = findPath(node, finalDistance, distanceMap);
                                findNodes(next, targetDistance - finalDistance - 1, forbiddenNodes, allowEntry, 0, finalLap, allowPitEntry)
                                    .forEach((n, dp) -> result.merge(
                                        n,
                                        dp.addPrefix(damage, path),
                                        (dp1, dp2) -> dp1.getDamage() <= dp2.getDamage() ? dp1 : dp2
                                    ));
                            }
                        } else {
                            // curve
                            addWork(next, finalDistance, distanceMap);
                        }
                    } else {
                        // straight
                        if (visited.add(next)) {
                            addWork(next, finalDistance, distanceMap);
                        }
                    }
                });
            }
        }
        return result;
    }

    public static Map<Node, DamageAndPath> findTargetNodes(Node node, int gear, int roll, int hitpoints, int curveStops, int lapsToGo, Set<Node> forbiddenNodes, boolean start) {
        final boolean finalLap = lapsToGo == 0;
        final boolean allowPitEntry = !finalLap && gear < 5 && !start;
        final Map<Node, DamageAndPath> result = NodeUtil.findNodes(node, roll, forbiddenNodes, true, curveStops, finalLap, allowPitEntry);
        final Map<Node, DamageAndPath> targets = new HashMap<>();
        for (Map.Entry<Node, DamageAndPath> entry : result.entrySet()) {
            if (entry.getValue().getDamage() < hitpoints) {
                targets.put(entry.getKey(), entry.getValue());
            }
        }
        return targets;
    }
}
