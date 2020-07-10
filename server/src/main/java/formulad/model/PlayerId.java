package formulad.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Unique identifer for the player
 */
public class PlayerId implements Serializable {
  private String playerId = null;

  public PlayerId playerId(String playerId) {
    this.playerId = playerId;
    return this;
  }

  public String getPlayerId() {
    return playerId;
  }
  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PlayerId playerId = (PlayerId) o;
    return Objects.equals(this.playerId, playerId.playerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(playerId);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PlayerId {\n");
    sb.append("    playerId: ").append(toIndentedString(playerId)).append("\n");
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
