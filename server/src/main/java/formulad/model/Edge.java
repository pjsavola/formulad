package formulad.model;

import java.io.Serializable;
import java.util.Objects;

public class Edge implements Serializable {
  private Node start = null;
  private Node end = null;

  public Edge start(Node start) {
    this.start = start;
    return this;
  }

  public Node getStart() {
    return start;
  }
  public void setStart(Node start) {
    this.start = start;
  }

  public Edge end(Node end) {
    this.end = end;
    return this;
  }

  public Node getEnd() {
    return end;
  }
  public void setEnd(Node end) {
    this.end = end;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Edge edge = (Edge) o;
    return Objects.equals(this.start, edge.start) &&
        Objects.equals(this.end, edge.end);
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, end);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Edge {\n");
    sb.append("    start: ").append(toIndentedString(start)).append("\n");
    sb.append("    end: ").append(toIndentedString(end)).append("\n");
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
