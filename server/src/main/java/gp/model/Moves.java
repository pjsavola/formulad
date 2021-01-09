package gp.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class Moves implements Serializable {
  private GameId game = null;
  private List<ValidMove> moves = null;

  public Moves game(GameId game) {
    this.game = game;
    return this;
  }

  public GameId getGame() {
    return game;
  }
  public void setGame(GameId game) {
    this.game = game;
  }

  public Moves moves(List<ValidMove> moves) {
    this.moves = moves;
    return this;
  }

  public List<ValidMove> getMoves() {
    return moves;
  }
  public void setMoves(List<ValidMove> moves) {
    this.moves = moves;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Moves moves = (Moves) o;
    return Objects.equals(this.game, moves.game) &&
        Objects.equals(this.moves, moves.moves);
  }

  @Override
  public int hashCode() {
    return Objects.hash(game, moves);
  }

  @Override
  public String toString() {
    return "class Moves {\n" +
            "    game: " + toIndentedString(game) + "\n" +
            "    moves: " + toIndentedString(moves) + "\n" +
            "}";
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}
