package formulad;

import java.util.ArrayList;
import java.util.List;

import formulad.ai.Node;

import fi.relex.model.model.Edge;
import fi.relex.model.model.GameId;
import fi.relex.model.model.GameState;
import fi.relex.model.model.InitializeTrack;
import fi.relex.model.model.PlayerId;
import fi.relex.model.model.PlayerState;
import fi.relex.model.model.Track;

public abstract class ApiHelper {
    public static Track buildTrack(String gameId, String playerId, List<Node> nodes) {
        final Track track = new Track()
            .game(new GameId().gameId(gameId))
            .player(new PlayerId().playerId(playerId));
        final InitializeTrack trackData = new InitializeTrack();
        final List<fi.relex.model.model.Node> apiNodes = new ArrayList<>();
        final List<Edge> apiEdges = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            final Node node = nodes.get(i);
            apiNodes.add(new fi.relex.model.model.Node()
                .nodeId(i)
                .type(fi.relex.model.model.Node.TypeEnum.valueOf(node.getType().name()))
            );
        }
        for (int i = 0; i < nodes.size(); i++) {
            final Node node = nodes.get(i);
            final fi.relex.model.model.Node apiNode = apiNodes.get(i);
            node.forEachChild(child -> {
                apiEdges.add(new Edge().start(apiNode).end(apiNodes.get(child.getId())));
            });
        }
        return track.track(trackData.nodes(apiNodes).edges(apiEdges));
    }

    public static GameState buildGameState(String gameId, List<LocalPlayer> players) {
        final GameState gameState = new GameState().game(new GameId().gameId(gameId));
        players.forEach(player -> {
            final PlayerState playerState = new PlayerState();
            player.populate(playerState);
            gameState.addPlayersItem(playerState);
        });
        return gameState;
    }
}
