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


package gp.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * A valid move a player can take containing the identifier of the node and what repercussions there will be for choosing that
 */
public class ValidMove implements Serializable {
  private Integer nodeId = null;
  private Integer overshoot = null;
  private Integer braking = null;

  public ValidMove nodeId(Integer nodeId) {
    this.nodeId = nodeId;
    return this;
  }

  public Integer getNodeId() {
    return nodeId;
  }
  public void setNodeId(Integer nodeId) {
    this.nodeId = nodeId;
  }

  public ValidMove overshoot(Integer overshoot) {
    this.overshoot = overshoot;
    return this;
  }

  public Integer getOvershoot() {
    return overshoot;
  }
  public void setOvershoot(Integer overshoot) {
    this.overshoot = overshoot;
  }

  public ValidMove braking(Integer braking) {
    this.braking = braking;
    return this;
  }

  public Integer getBraking() {
    return braking;
  }
  public void setBraking(Integer braking) {
    this.braking = braking;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ValidMove validMove = (ValidMove) o;
    return
        Objects.equals(this.nodeId, validMove.nodeId) &&
        Objects.equals(this.overshoot, validMove.overshoot) &&
        Objects.equals(this.braking, validMove.braking);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId, overshoot, braking);
  }

  @Override
  public String toString() {
    return "class ValidMove {\n" +
            "    nodeId: " + toIndentedString(nodeId) + "\n" +
            "    overshoot: " + toIndentedString(overshoot) + "\n" +
            "    braking: " + toIndentedString(braking) + "\n" +
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
