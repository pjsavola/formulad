package formulad.ai;

public interface AI {

    String getName();

    boolean isActive();

    /**
     * Server calls each AI once with this method, assigning player
     * identifer and start node identifier for the players. It also sends
     * the game map as two dimensional arrays, which should be interpreted
     * like this:
     *
     * - nodes[i][0] contains node identifier for node i
     * - nodes[i][1] contains node type for node i
     * - edges[j][0] contains start node identifier for edge j
     * - edges[j][1] contains end node identifier for edge j
     *
     */
    void initialize(int playerId, int startNodeId, int[][] nodes, int[][] edges);

    /**
     * When server expects AI to make a move, it calls the AI with
     * this method, which should be interpreted like this:
     *
     * - players[i][0] contains player identifier of player i
     * - players[i][1] contains node identifier of the location of player i
     * - players[i][2] contains remaining hitpoints of player i
     * - players[i][3] contains current gear of player i
     * - players[i][4] contains curve stops made by player i
     *
     * AI is expected to return gear used for next dice roll.
     *
     */
    int selectGear(int[][] players);

    /**
     * After rolling dice, server calculates all valid target nodes for the AI
     * and calls this method, providing all possible targets as a two dimensional
     * array, which should be interpreted like
     * this:
     *
     * - targets[i][0] contains node identifier of target i
     * - targets[i][1] contains damage taken from overshoot if this target is chosen
     * - targets[i][2] contains damage taken from braking if this target is chosen
     *
     * AI is expected to return index of selected target i.
     *
     */
    int selectTarget(int[][] targets);

    /**
     * When server does not expect any more reponses from the AI, this
     * method is called. It happens after hitpoints drop to zero or the finish
     * line is crossed.
     *
     */
    void sendGameOver();
}
