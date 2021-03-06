package gp.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GameState implements Serializable {
  private GameId game = null;
  private List<PlayerState> players = new ArrayList<>();

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

  public void addPlayersItem(PlayerState playersItem) {
    this.players.add(playersItem);
  }

  public List<PlayerState> getPlayers() {
    return players;
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
      return "class GameState {\n" +
              "    game: " + toIndentedString(game) + "\n" +
              "    players: " + toIndentedString(players) + "\n" +
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
