package gp.ai;

import java.util.*;
import java.util.stream.Collectors;

import gp.model.GameState;
import gp.model.PlayerState;

public abstract class AIUtil {

    /**
     * Builds map from player identifier to player state from server input.
     */
    public static Map<String, PlayerState> buildPlayerMap(GameState gameState) {
        final Map<String, PlayerState> playerMap = new HashMap<>();
        gameState.getPlayers().forEach(player -> playerMap.put(player.getPlayerId(), player));
        return playerMap;
    }

    /**
     * Builds a map which contains parent nodes for each node.
     */
    public static Map<Node, List<Node>> buildPrevNodeMap(Collection<Node> nodes) {
        final Map<Node, List<Node>> prevNodeMap = new HashMap<>();
        for (Node node : nodes) {
            node.forEachChild(child -> {
                prevNodeMap.computeIfAbsent(child, _node -> new ArrayList<>()).add(node);
            });
        }
        return prevNodeMap;
    }

    /**
     * Gets locations of all players as a set, which can be further used e.g. when performing searchs.
     */
    public static Set<Node> getForbiddenNodes(Map<String, PlayerState> playerMap, Map<Integer, Node> nodeMap) {
        return playerMap.values().stream().map(player -> nodeMap.get(player.getNodeId())).collect(Collectors.toSet());
    }

    /**
     * Returns true if the given player can select the given gear.
     */
    public static boolean validateGear(PlayerState player, int newGear, boolean inPits) {
        return validateGear(player.getHitpoints(), player.getGear(), newGear, inPits);
    }

    /**
     * Returns true if new gear can be selected if the player currently has old gear and given number of hitpoints.
     */
    public static boolean validateGear(int hitpoints, int oldGear, int newGear, boolean inPits) {
        if (newGear < 1 || newGear > 6) return false;

        if (inPits && newGear > 4) return false;

        if (Math.abs(newGear - oldGear) <= 1) return true;

        final int damage = oldGear - newGear - 1;
        return damage > 0 && damage < 4 && hitpoints > damage;
    }

    // Returns -1 if the next curve is not reachable. Otherwise returns the minimum distance to the curve.
    public static int getMinDistanceToNextCurve(Node node, Set<Node> blockedNodes) {
        if (node.isCurve()) {
            final Map<Node, Integer> nextStraight = findMinDistancesToNextAreaStart(node, false, blockedNodes);
            if (nextStraight.isEmpty()) {
                return -1;
            }
            return nextStraight
                    .entrySet()
                    .stream()
                    .map(e -> {
                        final int extraDistance = findMinDistancesToNextAreaStart(e.getKey(), false, blockedNodes).values().stream().mapToInt(Integer::intValue).min().orElse(-1);
                        return extraDistance == -1 ? -1 : extraDistance + e.getValue();
                    })
                    .mapToInt(Integer::intValue)
                    .filter(i -> i != -1)
                    .min()
                    .orElse(-1);
        } else {
            return findMinDistancesToNextAreaStart(node, false, blockedNodes).values().stream().mapToInt(Integer::intValue).min().orElse(-1);
        }
    }

    // Returns the maximum distance without taking damage.
    public static int getMaxDistanceWithoutDamage(Node startNode, int stopCount, Set<Node> blockedNodes) {
        if (startNode.getStopCount() > stopCount) {
            return findMaxDistanceInThisArea(startNode, blockedNodes);
        }
        final Map<Node, Integer> nextAreaStart = findMaxDistancesToNextAreaStart(startNode, blockedNodes);
        if (nextAreaStart.isEmpty()) {
            return findMaxDistanceInThisArea(startNode, blockedNodes);
        }
        return nextAreaStart
                .entrySet()
                .stream()
                .map(e -> getMaxDistanceWithoutDamage(e.getKey(), 0, blockedNodes) + e.getValue())
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    // Returns the minimum distance to take damage without opponents.
    public static int getMinDistanceToTakeDamage(Node startNode, int stopCount) {
        final Map<Node, Integer> nextAreaStart = findMinDistancesToNextAreaStart(startNode, false);
        if (nextAreaStart.isEmpty()) {
            return 1;
        }
        if (startNode.getStopCount() > stopCount) {
            return nextAreaStart.values().stream().mapToInt(Integer::intValue).min().orElse(1);
        }
        return nextAreaStart
                .entrySet()
                .stream()
                .map(e -> getMinDistanceToTakeDamage(e.getKey(), 0) + e.getValue())
                .mapToInt(Integer::intValue)
                .min()
                .orElse(1);
    }

    public static int getMinDistanceToPits(Node startNode, Set<Node> blockedNodes) {
        final Deque<Node> work = new ArrayDeque<>();
        final Map<Node, Integer> distances = new HashMap<>();
        distances.put(startNode, 0);
        work.addLast(startNode);
        while (!work.isEmpty()) {
            final Node node = work.removeFirst();
            final int newDistance = distances.get(node) + 1;
            node.childStream().filter(child -> !blockedNodes.contains(child)).forEach(child -> {
                final Integer distance = distances.get(child);
                if (distance == null) {
                    distances.put(child, newDistance);
                    if (child.isPit()) {
                        work.clear();
                    } else {
                        work.addLast(child);
                    }
                }
            });
        }
        return distances.values().stream().mapToInt(Integer::intValue).max().orElse(-1);
    }

    private static int findMaxDistanceInThisArea(Node startNode, Set<Node> blockedNodes) {
        final boolean startNodeIsCurve = startNode.isCurve();
        final Deque<Node> work = new ArrayDeque<>();
        final Map<Node, Integer> matchingTypeDistances = new HashMap<>();
        matchingTypeDistances.put(startNode, 0);
        work.addLast(startNode);
        while (!work.isEmpty()) {
            final Node node = work.removeFirst();
            final int newDistance = matchingTypeDistances.get(node) + 1;
            node.childStream().filter(child -> !blockedNodes.contains(child)).forEach(child -> {
                if (child.isCurve() == startNodeIsCurve) {
                    final Integer distance = matchingTypeDistances.get(child);
                    if (distance == null || (startNodeIsCurve && newDistance > distance)) {
                        matchingTypeDistances.put(child, newDistance);
                        work.addLast(child);
                    }
                }
            });
        }
        return matchingTypeDistances.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    static int getStopsRequiredInNextCurve(Node startNode) {
        final Node nonCurve;
        if (startNode.isCurve()) {
            nonCurve = recurseWhile(startNode, true, false);
        } else {
            nonCurve = startNode;
        }
        final Node nextCurve = recurseWhile(nonCurve, false, startNode.isPit());
        return nextCurve.getStopCount();
    }

    static Node recurseWhile(Node node, boolean isCurve, boolean inPits) {
        if (node.isCurve() == isCurve) {
            final Node next;
            if (inPits) {
                next = node.childStream().findAny().orElse(null);
            } else {
                next = node.childStream().filter(child -> !child.isPit()).findAny().orElse(null);
            }
            return next == null ? node : recurseWhile(next, isCurve, inPits);
        } else {
            return node;
        }
    }

    static int findMaxDistanceToStraight(Node startNode) {
        if (!startNode.isCurve()) {
            return 0;
        }
        return startNode
                .childStream()
                .map(AIUtil::findMaxDistanceToStraight)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
    }

    static int getMaxDistanceToNextStraight(Node startNode) {
        if (startNode.isCurve()) {
            return findMaxDistanceToStraight(startNode);
        } else {
            return findMaxDistancesToNextAreaStart(startNode)
                    .entrySet()
                    .stream()
                    .map(e -> findMaxDistanceToStraight(e.getKey()) + e.getValue())
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
        }
    }

    static int getMaxDistanceToStraightAfterNextCurve(Node startNode) {
        if (startNode.isCurve()) {
            return findMaxDistancesToNextAreaStart(startNode)
                    .entrySet()
                    .stream()
                    .map(e -> getMaxDistanceToNextStraight(e.getKey()) + e.getValue())
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
        } else {
            return getMaxDistanceToNextStraight(startNode);
        }
    }

    static Map<Node, Integer> findMaxDistancesToNextAreaStart(Node startNode) {
        return findMaxDistancesToNextAreaStart(startNode, Collections.emptySet());
    }

    static Map<Node, Integer> findMaxDistancesToNextAreaStart(Node startNode, Set<Node> blockedNodes) {
        final NodeType type = startNode.getType();
        if (startNode.isCurve() || startNode.isPit()) {
            final Deque<Node> work = new ArrayDeque<>();
            final Map<Node, Integer> matchingTypeDistances = new HashMap<>();
            final Map<Node, Integer> nonMatchingTypeDistances = new HashMap<>();
            matchingTypeDistances.put(startNode, 0);
            work.addLast(startNode);
            while (!work.isEmpty()) {
                final Node node = work.removeLast();
                final int newDistance = matchingTypeDistances.get(node) + 1;
                node.childStream().filter(child -> !blockedNodes.contains(child)).forEach(child -> {
                    if (child.getType() == type) {
                        final Integer distance = matchingTypeDistances.get(child);
                        if (distance == null || newDistance > distance) {
                            matchingTypeDistances.put(child, newDistance);
                            work.addLast(child);
                        }
                    } else {
                        final Integer distance = nonMatchingTypeDistances.get(child);
                        if (distance == null || newDistance > distance) {
                            nonMatchingTypeDistances.put(child, newDistance);
                        }
                    }
                });
            }
            return nonMatchingTypeDistances;
        } else {
            return findMinDistancesToNextAreaStart(startNode, true, blockedNodes);
        }
    }

    static Map<Node, Integer> findMinDistancesToNextAreaStart(Node startNode, boolean allowNonOptimalLastMove) {
        return findMinDistancesToNextAreaStart(startNode, allowNonOptimalLastMove, Collections.emptySet());
    }

    static Map<Node, Integer> findMinDistancesToNextAreaStart(Node startNode, boolean allowNonOptimalLastMove, Set<Node> blockedNodes) {
        final boolean startNodeIsCurve = startNode.isCurve();
        final Deque<Node> work = new ArrayDeque<>();
        final Map<Node, Integer> matchingTypeDistances = new HashMap<>();
        final Map<Node, Integer> nonMatchingTypeDistances = new HashMap<>();
        matchingTypeDistances.put(startNode, 0);
        work.addLast(startNode);
        while (!work.isEmpty()) {
            final Node node = work.removeFirst();
            final int newDistance = matchingTypeDistances.get(node) + 1;
            node.childStream().filter(child -> !blockedNodes.contains(child)).forEach(child -> {
                if (child.isCurve() == startNodeIsCurve) {
                    final Integer distance = matchingTypeDistances.get(child);
                    if (distance == null) {
                        matchingTypeDistances.put(child, newDistance);
                        work.addLast(child);
                    }
                } else {
                    final Integer distance = nonMatchingTypeDistances.get(child);
                    if (distance == null || (allowNonOptimalLastMove && newDistance > distance)) {
                        nonMatchingTypeDistances.put(child, newDistance);
                    }
                }
            });
        }
        return nonMatchingTypeDistances;
    }
}
