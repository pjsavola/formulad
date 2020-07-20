package gp.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Unique identifier for the node
 */
public class NodeId implements Serializable {
  private Integer nodeId = null;

  public NodeId nodeId(Integer nodeId) {
    this.nodeId = nodeId;
    return this;
  }

  public Integer getNodeId() {
    return nodeId;
  }
  public void setNodeId(Integer nodeId) {
    this.nodeId = nodeId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NodeId nodeId = (NodeId) o;
    return Objects.equals(this.nodeId, nodeId.nodeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class NodeId {\n");
    sb.append("    nodeId: ").append(toIndentedString(nodeId)).append("\n");
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
