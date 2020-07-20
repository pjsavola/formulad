package gp.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GameState implements Serializable {
  private GameId game = null;
  private List<PlayerState> players = new ArrayList<PlayerState>();

  public GameState game(GameId game) {
    this.game = game;
    return this;
  }

  public GameId getGame() {
    return game;
  }
  public void setGame(GameId game) {
    this.game = game;
  }

  public GameState players(List<PlayerState> players) {
    this.players = players;
    return this;
  }

  public GameState addPlayersItem(PlayerState playersItem) {
    this.players.add(playersItem);
    return this;
  }

  public List<PlayerState> getPlayers() {
    return players;
  }
 public void setPlayers(List<PlayerState> players) {
    this.players = players;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GameState gameState = (GameState) o;
    return Objects.equals(this.game, gameState.game) &&
        Objects.equals(this.players, gameState.players);
  }

  @Override
  public int hashCode() {
    return Objects.hash(game, players);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class GameState {\n");
    sb.append("    game: ").append(toIndentedString(game)).append("\n");
    sb.append("    players: ").append(toIndentedString(players)).append("\n");
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
