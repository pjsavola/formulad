package gp.model;

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

    public int getOvershootDamage() {
        switch (type) {
            case HARD: return 1;
            case SOFT: return age < 3 ? 2 : 3;
            case WET: return 2;
        }
        throw new RuntimeException("Invalid tire type");
    }

    public boolean canUse() {
        return type == Type.SOFT && age <= 1;
    }

    public void increaseAge() {
        ++age;
    }
}
