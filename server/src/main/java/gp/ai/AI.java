package gp.ai;

import gp.model.GameState;
import gp.model.Gear;
import gp.model.Moves;
import gp.model.NameAtStart;
import gp.model.SelectedIndex;
import gp.model.Track;

public interface AI {

    /**
     * Game server calls each AI server once with this method to send track
     * data.
     *
     * AI is expected to return its name as a String.
     *
     */
    NameAtStart startGame(Track track);

    /**
     * When game server expects AI to make a move, it calls the AI server with
     * this method, providing the current game state as parameter.
     *
     * AI is expected to return gear used for next dice roll. See class
     * gp.ai.Gear for more details on dice distributions.
     *
     */
    Gear selectGear(GameState gameState);

    /**
     * After rolling dice, game server calculates all valid moves for the AI and
     * calls this method, providing a non-empty list of valid moves.
     *
     * AI is expected to return an index to the list of valid moves to indicate
     * selection of that move.
     *
     */
    SelectedIndex selectMove(Moves moves);

    default void notify(Object notification) {
    }
}