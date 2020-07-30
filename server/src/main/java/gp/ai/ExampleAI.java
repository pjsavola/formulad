package gp.ai;

import java.util.*;
import java.util.logging.Level;

import gp.Main;
import gp.model.*;

public class ExampleAI extends BaseAI {

    private Map<String, PlayerState> playerMap;
    private Node location;
    private PlayerState player;
    private Random random = new Random();

    public ExampleAI(TrackData data) {
        super(data);
    }

    @Override
    public gp.model.Gear selectGear(GameState gameState) {
        playerMap = AIUtil.buildPlayerMap(gameState);
        player = playerMap.get(playerId);
        location = data.getNodes().get(player.getNodeId());

        // TODO: Implement better AI
        int newGear = player.getGear() + 1;
        if (newGear > 4) newGear = 4;
        return new gp.model.Gear().gear(newGear);
    }

    @Override
    public SelectedIndex selectMove(Moves moves) {
        // TODO: Implement better AI
        int leastDamage = player.getHitpoints();
        final List<ValidMove> validMoves = moves.getMoves();
        final List<Integer> bestTargets = new ArrayList<>();
        for (int i = 0; i < validMoves.size(); i++) {
            final ValidMove vm = validMoves.get(i);
            final int damage = vm.getBraking() + vm.getOvershoot();
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
            return new SelectedIndex().index(0);
        }
        return new SelectedIndex().index(bestTargets.get(random.nextInt(bestTargets.size())));
    }
}
