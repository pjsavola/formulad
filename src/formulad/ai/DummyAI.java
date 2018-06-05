package formulad.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DummyAI implements AI {

    private int playerId;
    private Map<Integer, Node> nodeMap;
    private Map<Integer, PlayerData> playerMap;
    private Node location;
    private PlayerData player;
    private Random random = new Random();

    @Override
    public String getName() {
        return "Dummy";
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void initialize(int playerId, int startNodeId, int[][] nodes, int[][] edges) {
        this.playerId = playerId;
        nodeMap = AIUtil.readNodeMap(nodes, edges);
        location = nodeMap.get(startNodeId);
        if (location == null) {
            throw new RuntimeException("Invalid start node identifier: " + startNodeId);
        }
    }

    @Override
    public int selectGear(int[][] players) {
        playerMap = AIUtil.readPlayerMap(players, nodeMap);
        player = playerMap.get(playerId);
        if (player == null) {
            throw new RuntimeException("No data sent for player: " + playerId);
        }
        // TODO: Implement better AI
        return Math.min(6, player.getGear() + 1);
    }

    @Override
    public int selectTarget(int[][] targets) {
        // TODO: Implement better AI
        if (targets.length == 0) {
            throw new RuntimeException("No valid targets provided by server!");
        }
        int leastDamage = player.getHitpoints();
        final List<Integer> bestTargets = new ArrayList<>();
        for (int i = 0; i < targets.length; i++) {
            final int[] target = targets[i];
            final int nodeId = target[0];
            final int damage = target[1] + target[2];
            if (damage < leastDamage) {
                bestTargets.clear();
                leastDamage = damage;
            }
            if (damage <= leastDamage) {
                bestTargets.add(i);
            }
        }
        if (bestTargets.isEmpty()) {
            // Flaw in this AI, just select something valid
            return 0;
        }
        return bestTargets.get(random.nextInt(bestTargets.size()));
    }

    @Override
    public void sendGameOver() {
    }
}
