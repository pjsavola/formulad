package gp.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Gear of the car. It can be be 0 in the start but 0 can never be chosen.
 */
public class Gear implements Serializable {
  private Integer gear = null;
  private Tires tires = null;

  public Gear gear(Integer gear) {
    this.gear = gear;
    return this;
  }

  public Integer getGear() {
    return gear;
  }

  public Gear tires(Tires tires) {
    this.tires = tires;
    return this;
  }

  public Tires getTires() {
    return tires;
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
    return Objects.equals(this.gear, gear.gear) && Objects.equals(this.tires, gear.tires);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gear, tires);
  }

  @Override
  public String toString() {
    return "class Gear {\n" +
            "    gear: " + toIndentedString(gear) + "\n" +
            "    tires: " + toIndentedString(tires == null ? null : tires.getType().toString()) + "\n" +
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
