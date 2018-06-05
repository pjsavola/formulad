package formulad.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AIUtil {

    public static Map<Integer, Node> readNodeMap(int[][] nodes, int[][] edges) {
        final Map<Integer, Node> nodeMap = new HashMap<>();
        for (int[] node : nodes) {
            final int nodeId = node[0];
            final int nodeType = node[1];
            if (nodeType < 1 || nodeType > 5) {
                throw new RuntimeException("Unknown node type: " + nodeType);
            }
            final Node oldNode = nodeMap.put(nodeId, new Node(nodeId, nodeType));
            if (oldNode != null) {
                throw new RuntimeException("Duplicate node identifier: " + nodeId);
            }
        }
        for (int[] edge : edges) {
            final Node start = nodeMap.get(edge[0]);
            final Node end = nodeMap.get(edge[1]);
            if (start == null || end == null) {
                throw new RuntimeException("Invalid node identifier for edge: " + edge[start == null ? 0 : 1]);
            }
            start.addChild(end);
        }
        return nodeMap;
    }

    public static Map<Integer, PlayerData> readPlayerMap(int[][] players, Map<Integer, Node> nodeMap) {
        final Map<Integer, PlayerData> playerMap = new HashMap<>();
        for (int[] player : players) {
            final int playerId = player[0];
            final int nodeId = player[1];
            final int hitpoints = player[2];
            final int gear = player[3];
            final int stops = player[4];
            final Node node = nodeMap.get(nodeId);
            if (node == null) {
                throw new RuntimeException("LocalPlayer " + playerId + " in invalid location: " + nodeId);
            }
            final PlayerData oldPlayer = playerMap.put(playerId, new PlayerData(playerId, node, hitpoints, gear, stops));
            if (oldPlayer != null) {
                throw new RuntimeException("Duplicate player identifier: " + playerId);
            }
        }
        return playerMap;
    }

    public static Set<Node> getForbiddenNodes(Map<Integer, PlayerData> playerMap) {
        return playerMap.values().stream().map(PlayerData::getNode).collect(Collectors.toSet());
    }

    public static boolean validateGear(PlayerData player, int newGear) {
        if (newGear < 1 || newGear > 6) return false;

        if (Math.abs(newGear - player.getGear()) <= 1) return true;

        final int damage = player.getGear() - newGear - 1;
        return damage > 0 && damage < 4 && player.getHitpoints() > damage;
    }
}
