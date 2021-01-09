package gp.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Gear of the car. It can be be 0 in the start but 0 can never be chosen.
 */
public class Gear implements Serializable {
  private Integer gear = null;

  public Gear gear(Integer gear) {
    this.gear = gear;
    return this;
  }

  public Integer getGear() {
    return gear;
  }
  public void setGear(Integer gear) {
    this.gear = gear;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Gear gear = (Gear) o;
    return Objects.equals(this.gear, gear.gear);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gear);
  }

  @Override
  public String toString() {
    return "class Gear {\n" +
            "    gear: " + toIndentedString(gear) + "\n" +
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
