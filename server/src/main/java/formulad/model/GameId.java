package formulad.model;

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

  public String getGameId() {
    return gameId;
  }
  public void setGameId(String gameId) {
    this.gameId = gameId;
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
    StringBuilder sb = new StringBuilder();
    sb.append("class GameId {\n");
    sb.append("    gameId: ").append(toIndentedString(gameId)).append("\n");
    sb.append("}");
    return sb.toString();
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
