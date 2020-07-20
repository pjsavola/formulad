package gp.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * State of a player: location, hit points, current gear and how many stops a player has made in the current curve
 */
public class PlayerState implements Serializable {
  private String playerId = null;
  private Integer gear = null;
  private TypeEnum type = null;
  private Integer nodeId = null;
  private Integer hitpoints = null;
  private Integer stops = null;
  private Integer leeway = null;

  public PlayerState playerId(String playerId) {
    this.playerId = playerId;
    return this;
  }

  public String getPlayerId() {
    return playerId;
  }
  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  public PlayerState gear(Integer gear) {
    this.gear = gear;
    return this;
  }

  public Integer getGear() {
    return gear;
  }
  public void setGear(Integer gear) {
    this.gear = gear;
  }

  public PlayerState type(TypeEnum type) {
    this.type = type;
    return this;
  }

  public TypeEnum getType() {
    return type;
  }
  public void setType(TypeEnum type) {
    this.type = type;
  }

  public PlayerState nodeId(Integer nodeId) {
    this.nodeId = nodeId;
    return this;
  }

  public Integer getNodeId() {
    return nodeId;
  }
  public void setNodeId(Integer nodeId) {
    this.nodeId = nodeId;
  }

  public PlayerState hitpoints(Integer hitpoints) {
    this.hitpoints = hitpoints;
    return this;
  }

  public Integer getHitpoints() {
    return hitpoints;
  }
  public void setHitpoints(Integer hitpoints) {
    this.hitpoints = hitpoints;
  }

  public PlayerState stops(Integer stops) {
    this.stops = stops;
    return this;
  }

   /**
   * The amount of stops the player has made in the current curve or zero.
  **/
  public Integer getStops() {
    return stops;
  }
  public void setStops(Integer stops) {
    this.stops = stops;
  }

  public PlayerState leeway(Integer leeway) {
    this.leeway = leeway;
    return this;
  }

   /**
   * The number of milliseconds the player can miss the time limits for the rest of the game.
  **/
  public Integer getLeeway() {
    return leeway;
  }
  public void setLeeway(Integer leeway) {
    this.leeway = leeway;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PlayerState playerState = (PlayerState) o;
    return Objects.equals(this.playerId, playerState.playerId) &&
        Objects.equals(this.gear, playerState.gear) &&
        Objects.equals(this.type, playerState.type) &&
        Objects.equals(this.nodeId, playerState.nodeId) &&
        Objects.equals(this.hitpoints, playerState.hitpoints) &&
        Objects.equals(this.stops, playerState.stops) &&
        Objects.equals(this.leeway, playerState.leeway);
  }

  @Override
  public int hashCode() {
    return Objects.hash(playerId, gear, type, nodeId, hitpoints, stops, leeway);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PlayerState {\n");
    sb.append("    playerId: ").append(toIndentedString(playerId)).append("\n");
    sb.append("    gear: ").append(toIndentedString(gear)).append("\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    nodeId: ").append(toIndentedString(nodeId)).append("\n");
    sb.append("    hitpoints: ").append(toIndentedString(hitpoints)).append("\n");
    sb.append("    stops: ").append(toIndentedString(stops)).append("\n");
    sb.append("    leeway: ").append(toIndentedString(leeway)).append("\n");
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
