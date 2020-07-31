package gp.ai;

import gp.model.GameState;
import gp.model.Gear;
import gp.model.Moves;
import gp.model.SelectedIndex;

public interface AI {

    enum Type { MANUAL, GREAT, MAGNIFICENT }

    static Type fromString(String str) {
        if (str == null) return null;
        switch (str) {
            case "gp.ai.ManualAI":      return Type.MANUAL;
            case "gp.ai.GreatAI":       return Type.GREAT;
            case "gp.ai.MagnificentAI": return Type.MAGNIFICENT;
        }
        return null;
    }

    static String toString(Type type) {
        if (type == null) return "gp.ai.ManualAI";
        switch (type) {
            case MANUAL:      return "gp.ai.ManualAI";
            case GREAT:       return "gp.ai.GreatAI";
            case MAGNIFICENT: return "gp.ai.MagnificentAI";
        }
        return null;
    }

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

    void notify(Object notification);
}
