package formulad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class NodeUtil {
    // Finds path from initial node to the given node at given distance
    private static List<Node> findPath(final Node node,
                                       final int distance,
                                       final Map<Integer, Set<Node>> distanceMap) {
        final List<Node> path = new ArrayList<>();
        path.add(node);
        for (int i = distance - 1; i >= 0; i--) {
            for (final Node previous : distanceMap.get(i)) {
                if (previous.nextNodes.contains(path.get(0))) {
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

    private static void addWork(final Node node,
                                final int currentDistance,
                                final Map<Integer, Set<Node>> distanceMap) {
        distanceMap.computeIfAbsent(currentDistance + 1, _node -> new HashSet<>()).add(node);
    }

    public static Map<Node, DamageAndPath> findNodes(final Node startNode,
                                                     final int targetDistance,
                                                     final Set<Node> forbiddenNodes,
                                                     final boolean allowCurveEntry,
                                                     final int stopsDone,
                                                     final boolean finalLap) {
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
            for (final Node node : distanceMap.get(distance)) {
                if (distance == targetDistance) {
                    result.put(node, new DamageAndPath(0, findPath(node, distance, distanceMap)));
                    continue;
                }
                if (finalLap && distance > 0 && node.type == MapEditor.FINISH) {
                    result.put(node, new DamageAndPath(0, findPath(node, distance, distanceMap)));
                    continue;
                }
                for (final Node next : node.nextNodes) {
                    if (forbiddenNodes.contains(next)) {
                        // node is blocked
                        continue;
                    }
                    if (node.isCurve() || next.isCurve()) {
                        if (!node.isCurve()) {
                            // entering curve
                            if (allowCurveEntry) {
                                addWork(next, distance, distanceMap);
                            }
                        } else if (!next.isCurve()) {
                            // exiting curve
                            final int stopsToDo = (node.type == MapEditor.CURVE_1 ? 1 : 2) - stopsDone;
                            if (stopsToDo <= 1) {
                                final boolean allowEntry = stopsToDo <= 0;
                                final int damage = stopsToDo <= 0 ? 0 : targetDistance - distance;
                                final List<Node> path = findPath(node, distance, distanceMap);
                                findNodes(next, targetDistance - distance - 1, forbiddenNodes, allowEntry, 0, finalLap)
                                    .forEach((n, dp) -> result.merge(
                                        n,
                                        dp.addPrefix(damage, path),
                                        (dp1, dp2) -> dp1.getDamage() <= dp2.getDamage() ? dp1 : dp2
                                    ));

                            }
                        } else {
                            // curve
                            addWork(next, distance, distanceMap);
                        }
                    } else {
                        // straight
                        if (visited.add(next)) {
                            addWork(next, distance, distanceMap);
                        }
                    }
                }
            }
        }
        return result;
    }
}
