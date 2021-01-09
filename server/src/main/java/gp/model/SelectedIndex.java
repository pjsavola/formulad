package gp.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Index to the provided list of valid moves, to indicate selection of the corresponding move
 */
public class SelectedIndex implements Serializable {
  private Integer index = null;

  public SelectedIndex index(Integer index) {
    this.index = index;
    return this;
  }

  public Integer getIndex() {
    return index;
  }
  public void setIndex(Integer index) {
    this.index = index;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SelectedIndex selectedIndex = (SelectedIndex) o;
    return Objects.equals(this.index, selectedIndex.index);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index);
  }

  @Override
  public String toString() {
    return "class SelectedIndex {\n" +
            "    index: " + toIndentedString(index) + "\n" +
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
