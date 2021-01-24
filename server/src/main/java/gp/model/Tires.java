package gp.model;

import java.awt.*;
import java.io.Serializable;

public class Tires implements Serializable {
    public enum Type { HARD, SOFT, WET }

    private static final Color soft1 = new Color(0xCC0000);
    private static final Color soft2 = new Color(0xFF0000);
    private static final Color soft3 = new Color(0xFF7777);
    private static final Color wet1 = new Color(0x5555FF);
    private static final Color wet2 = new Color(0x9999FF);
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
                return age < 3 ? wet1 : wet2;
            case SOFT:
                return age <= 1 ? soft1 : (age < 3 ? soft2 : soft3);
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
