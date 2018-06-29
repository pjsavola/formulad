/*
 * Formula D
 * Simple API for a Formula D AI server.
 *
 * OpenAPI spec version: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package fi.relex.model.model;

import java.util.Objects;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import fi.relex.model.model.GameId;
import fi.relex.model.model.ValidMove;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Moves
 */
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2018-06-13T23:19:30.736+03:00")
public class Moves {
  @SerializedName("game")
  private GameId game = null;

  @SerializedName("moves")
  private List<ValidMove> moves = null;

  public Moves game(GameId game) {
    this.game = game;
    return this;
  }

   /**
   * Get game
   * @return game
  **/
  @ApiModelProperty(value = "")
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

  public Moves addMovesItem(ValidMove movesItem) {
    if (this.moves == null) {
      this.moves = new ArrayList<ValidMove>();
    }
    this.moves.add(movesItem);
    return this;
  }

   /**
   * Get moves
   * @return moves
  **/
  @ApiModelProperty(value = "")
  public List<ValidMove> getMoves() {
    return moves;
  }

  public void setMoves(List<ValidMove> moves) {
    this.moves = moves;
  }


  @Override
  public boolean equals(java.lang.Object o) {
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
    StringBuilder sb = new StringBuilder();
    sb.append("class Moves {\n");
    
    sb.append("    game: ").append(toIndentedString(game)).append("\n");
    sb.append("    moves: ").append(toIndentedString(moves)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

