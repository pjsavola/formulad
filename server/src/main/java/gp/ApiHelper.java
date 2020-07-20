package gp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gp.ai.Node;

import gp.model.Edge;
import gp.model.GameId;
import gp.model.GameState;
import gp.model.InitializeTrack;
import gp.model.PlayerId;
import gp.model.PlayerState;
import gp.model.Track;

public abstract class ApiHelper {
    public static Track buildTrack(String gameId, String playerId, List<Node> nodes, Map<Node, Double> attributes) {
        final Track track = new Track()
            .game(new GameId().gameId(gameId))
            .player(new PlayerId().playerId(playerId));
        final InitializeTrack trackData = new InitializeTrack();
        final List<gp.model.Node> apiNodes = new ArrayList<>();
        final List<Edge> apiEdges = new ArrayList<>();
        final List<gp.model.Node> garageNodes = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            final Node node = nodes.get(i);
            apiNodes.add(new gp.model.Node()
                .nodeId(i)
                .type(gp.model.TypeEnum.valueOf(node.getType().name()))
            );
            if (node.getType() == Node.Type.PIT && attributes.containsKey(node)) {
                garageNodes.add(apiNodes.get(apiNodes.size() - 1));
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            final Node node = nodes.get(i);
            final gp.model.Node apiNode = apiNodes.get(i);
            node.forEachChild(child -> {
                apiEdges.add(new Edge().start(apiNode).end(apiNodes.get(child.getId())));
            });
        }
        return track.track(trackData.nodes(apiNodes).edges(apiEdges).garageNodes(garageNodes));
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
