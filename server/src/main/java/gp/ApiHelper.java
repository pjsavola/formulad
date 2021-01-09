package gp;

import java.util.List;

import gp.model.GameId;
import gp.model.GameState;
import gp.model.PlayerState;

abstract class ApiHelper {
    static GameState buildGameState(String gameId, List<LocalPlayer> players) {
        final GameState gameState = new GameState().game(new GameId().gameId(gameId));
        for (LocalPlayer player : players) {
            final PlayerState playerState = new PlayerState();
            player.populate(playerState);
            gameState.addPlayersItem(playerState);
        }
        return gameState;
    }
}
