package gp.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * State of a player: location, hit points, current gear and how many stops a player has made in the current curve
 */
public class PlayerState implements Serializable {
  private String playerId = null;
  private Integer gear = null;
  private Integer nodeId = null;
  private Integer hitpoints = null;
  private Integer stops = null;
  private Integer leeway = null;
  private Integer lapsToGo = null;

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

   /**
   * The number of milliseconds the player can miss the time limits for the rest of the game.
  **/
  public Integer getLeeway() {
    return leeway;
  }
  public void setLeeway(Integer leeway) {
    this.leeway = leeway;
  }

  public Integer getLapsToGo() {
    return lapsToGo;
  }

  public void setLapsToGo(Integer lapsToGo) {
    this.lapsToGo = lapsToGo;
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
        Objects.equals(this.nodeId, playerState.nodeId) &&
        Objects.equals(this.hitpoints, playerState.hitpoints) &&
        Objects.equals(this.stops, playerState.stops) &&
        Objects.equals(this.leeway, playerState.leeway) &&
        Objects.equals(this.lapsToGo, playerState.lapsToGo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(playerId, gear, nodeId, hitpoints, stops, leeway, lapsToGo);
  }

  @Override
  public String toString() {
    return "class PlayerState {\n" +
            "    playerId: " + toIndentedString(playerId) + "\n" +
            "    gear: " + toIndentedString(gear) + "\n" +
            "    nodeId: " + toIndentedString(nodeId) + "\n" +
            "    hitpoints: " + toIndentedString(hitpoints) + "\n" +
            "    stops: " + toIndentedString(stops) + "\n" +
            "    leeway: " + toIndentedString(leeway) + "\n" +
            "    lapsToGo: " + toIndentedString(lapsToGo) + "\n" +
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
