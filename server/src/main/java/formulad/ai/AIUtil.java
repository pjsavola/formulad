package formulad.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import formulad.model.Edge;
import formulad.model.GameState;
import formulad.model.PlayerState;

public abstract class AIUtil {

    /**
     * Builds map from node identifier to node from server input.
     */
    public static Map<Integer, Node> buildNodeMap(List<formulad.model.Node> nodes, List<Edge> edges) {
        final Map<Integer, Node> nodeMap = new HashMap<>();
        nodes.forEach(node -> {
            final Node oldNode = nodeMap.put(node.getNodeId(), new Node(node));
            if (oldNode != null) {
                throw new RuntimeException("Duplicate node identifier: " + node.getNodeId());
            }
        });
        edges.forEach(edge -> {
            final Node start = nodeMap.get(edge.getStart().getNodeId());
            final Node end = nodeMap.get(edge.getEnd().getNodeId());
            if (start == null || end == null) {
                throw new RuntimeException("Invalid start or end node identifier for edge");
            }
            start.addChild(end);
        });
        return nodeMap;
    }

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

        if (Math.abs(newGear - oldGear) <= 1) return true;

        if (inPits && newGear > 4) return false;

        final int damage = oldGear - newGear - 1;
        return damage > 0 && damage < 4 && hitpoints > damage;
    }
}
