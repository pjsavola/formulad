/*
 * Formula D
 * Simple API for a Formula D AI server.
 *
 * OpenAPI spec version: 1.0.0
 *
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package fi.relex.model.handler;

import org.junit.Ignore;
import org.junit.Test;

import fi.relex.model.invoker.ApiException;
import fi.relex.model.model.GameState;
import fi.relex.model.model.Gear;
import fi.relex.model.model.Moves;
import fi.relex.model.model.NameAtStart;
import fi.relex.model.model.SelectedIndex;
import fi.relex.model.model.Track;

/**
 * API tests for DefaultApi
 */
@Ignore
public class DefaultApiTest {

    private final DefaultApi api = new DefaultApi();


    /**
     *
     *
     * Returns new move given the game state
     *
     * @throws ApiException
     *          if the Api call fails
     */
    @Test
    public void selectGearTest() throws ApiException {
        GameState gameState = null;
        Gear response = api.selectGear(gameState);

        // TODO: test validations
    }

    /**
     *
     *
     * Returns the identifier of the node to move to
     *
     * @throws ApiException
     *          if the Api call fails
     */
    @Test
    public void selectMoveTest() throws ApiException {
        Moves moves = null;
        SelectedIndex response = api.selectMove(moves);

        // TODO: test validations
    }

    /**
     *
     *
     * Starts a new game
     *
     * @throws ApiException
     *          if the Api call fails
     */
    @Test
    public void startGameTest() throws ApiException {
        Track track = null;
        NameAtStart response = api.startGame(track);

        // TODO: test validations
    }

}
