package gp.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Track as a graph
 */
public class InitializeTrack implements Serializable {
  private List<Node> nodes = new ArrayList<Node>();
  private List<Edge> edges = new ArrayList<Edge>();
  private List<Node> garageNodes = new ArrayList<>();

  public InitializeTrack nodes(List<Node> nodes) {
    this.nodes = nodes;
    return this;
  }

  public InitializeTrack addNodesItem(Node nodesItem) {
    this.nodes.add(nodesItem);
    return this;
  }

  public List<Node> getNodes() {
    return nodes;
  }
  public void setNodes(List<Node> nodes) {
    this.nodes = nodes;
  }

  public InitializeTrack edges(List<Edge> edges) {
    this.edges = edges;
    return this;
  }

  public InitializeTrack addEdgesItem(Edge edgesItem) {
    this.edges.add(edgesItem);
    return this;
  }

  public List<Edge> getEdges() {
    return edges;
  }
  public void setEdges(List<Edge> edges) {
    this.edges = edges;
  }

  public InitializeTrack garageNodes(List<Node> nodes) {
    this.garageNodes = nodes;
    return this;
  }

  public InitializeTrack addGarageNodesItem(Node nodesItem) {
    this.garageNodes.add(nodesItem);
    return this;
  }

  public List<Node> getGarageNodes() {
    return garageNodes;
  }
  public void setGarageNodes(List<Node> nodes) {
    this.garageNodes = nodes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InitializeTrack initializeTrack = (InitializeTrack) o;
    return Objects.equals(this.nodes, initializeTrack.nodes) &&
        Objects.equals(this.edges, initializeTrack.edges) &&
        Objects.equals(this.garageNodes, initializeTrack.garageNodes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodes, edges, garageNodes);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class InitializeTrack {\n");
    sb.append("    nodes: ").append(toIndentedString(nodes)).append("\n");
    sb.append("    edges: ").append(toIndentedString(edges)).append("\n");
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
