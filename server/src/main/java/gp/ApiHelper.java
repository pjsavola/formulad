package gp;

import java.util.List;

import gp.model.GameId;
import gp.model.GameState;
import gp.model.PlayerState;
import gp.model.Weather;

abstract class ApiHelper {
    static GameState buildGameState(String gameId, List<LocalPlayer> players, Weather weather) {
        final GameState gameState = new GameState().game(new GameId().gameId(gameId));
        for (LocalPlayer player : players) {
            final PlayerState playerState = new PlayerState();
            player.populate(playerState);
            gameState.addPlayersItem(playerState);
        }
        gameState.setWeather(weather);
        return gameState;
    }
}
