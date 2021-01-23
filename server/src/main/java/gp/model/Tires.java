package gp.model;

import java.awt.*;
import java.io.Serializable;

public class Tires implements Serializable {
    public enum Type { HARD, SOFT, WET }

    private final Type type;
    private int age;

    public Tires(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public int getOvershootDamage(Weather weather) {
        if (weather == null) return 1;
        switch (type) {
            case HARD:
                return 1;
            case WET:
                if (weather == Weather.RAIN) {
                    return 1;
                }
            case SOFT:
                return age < 3 ? 2 : 3;
        }
        throw new RuntimeException("Invalid tire type");
    }

    public boolean canUse(Weather weather) {
        return weather != null && weather != Weather.RAIN && type == Type.SOFT && age <= 1;
    }

    public int getAge() {
        return age;
    }

    public void increaseAge() {
        ++age;
    }

    public Color getColor() {
        switch (type) {
            case HARD:
                return Color.BLACK;
            case WET:
                return age < 3 ? Color.BLUE : Color.CYAN;
            case SOFT:
                return age <= 1 ? Color.RED : (age < 3 ? Color.PINK : Color.WHITE);
        }
        throw new RuntimeException("Invalid tire type");
    }

    public Color getColor2() {
        switch (type) {
            case HARD:
                return Color.DARK_GRAY;
            case WET:
            case SOFT:
                return getColor();
        }
        throw new RuntimeException("Invalid tire type");
    }
}
