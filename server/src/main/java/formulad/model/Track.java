package formulad.model;

import java.io.Serializable;
import java.util.Objects;

public class Track implements Serializable {
  private PlayerId player = null;
  private GameId game = null;
  private InitializeTrack track = null;

  public Track player(PlayerId player) {
    this.player = player;
    return this;
  }

  public PlayerId getPlayer() {
    return player;
  }
  public void setPlayer(PlayerId player) {
    this.player = player;
  }

  public Track game(GameId game) {
    this.game = game;
    return this;
  }

  public GameId getGame() {
    return game;
  }
  public void setGame(GameId game) {
    this.game = game;
  }

  public Track track(InitializeTrack track) {
    this.track = track;
    return this;
  }

  public InitializeTrack getTrack() {
    return track;
  }
  public void setTrack(InitializeTrack track) {
    this.track = track;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Track track = (Track) o;
    return Objects.equals(this.player, track.player) &&
        Objects.equals(this.game, track.game) &&
        Objects.equals(this.track, track.track);
  }

  @Override
  public int hashCode() {
    return Objects.hash(player, game, track);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Track {\n");
    sb.append("    player: ").append(toIndentedString(player)).append("\n");
    sb.append("    game: ").append(toIndentedString(game)).append("\n");
    sb.append("    track: ").append(toIndentedString(track)).append("\n");
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
