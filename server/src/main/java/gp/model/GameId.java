package gp.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Unique identifier for the game
 */
public class GameId implements Serializable {
  private String gameId = null;

  public GameId gameId(String gameId) {
    this.gameId = gameId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GameId gameId = (GameId) o;
    return Objects.equals(this.gameId, gameId.gameId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gameId);
  }

  @Override
  public String toString() {
    return "class GameId {\n" +
            "    gameId: " + toIndentedString(gameId) + "\n" +
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
