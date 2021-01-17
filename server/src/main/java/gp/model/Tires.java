package gp.model;

import java.io.Serializable;

public class Tires implements Serializable {
    public enum Type { HARD, SOFT, WET }

    private final Type type;
    private int age;
    private boolean used;

    public Tires(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public int getAge() {
        return age;
    }

    public boolean canUse() {
        return type == Type.SOFT && !used;
    }

    public void increaseAge() {
        ++age;
    }

    public void use() {
        used = true;
    }
}
