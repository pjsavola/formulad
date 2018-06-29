package formulad;

import java.util.logging.Level;

import formulad.ai.AI;

import fi.relex.model.handler.DefaultApi;
import fi.relex.model.invoker.ApiClient;
import fi.relex.model.invoker.ApiException;
import fi.relex.model.model.GameState;
import fi.relex.model.model.Gear;
import fi.relex.model.model.Moves;
import fi.relex.model.model.NameAtStart;
import fi.relex.model.model.SelectedIndex;
import fi.relex.model.model.Track;

public class RemoteAI implements AI {

    final DefaultApi api = new DefaultApi();

    public RemoteAI(String url) {
        api.setApiClient(new ApiClient().setBasePath(url));
    }

    @Override
    public NameAtStart startGame(Track track) {
        try {
            return api.startGame(track);
        } catch (ApiException e) {
            FormulaD.log.log(Level.SEVERE, "Error in remote AI", e);
            return null;
        }
    }

    @Override
    public Gear selectGear(GameState gameState) {
        try {
            return api.selectGear(gameState);
        } catch (ApiException e) {
            FormulaD.log.log(Level.SEVERE, "Error in remote AI", e);
            return null;
        }

    }

    @Override
    public SelectedIndex selectMove(Moves allMoves) {
        try {
            return api.selectMove(allMoves);
        } catch (ApiException e) {
            FormulaD.log.log(Level.SEVERE, "Error in remote AI", e);
            return null;
        }
    }
}
