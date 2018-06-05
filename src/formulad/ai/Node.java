package formulad.ai;

import java.util.ArrayList;
import java.util.List;

public final class Node {
    public enum Type { UNKNOWN, STRAIGHT, CURVE_1, CURVE_2, START, FINISH, CURVE_3 };

    private final int id;
    private final Type type;
    private final List<Node> children = new ArrayList<>();

    public Node(int id, int type) {
        this(id, Type.values()[type]);
    }

    public Node(int id, Type type) {
        this.id = id;
        this.type = type;
    }

    public void addChild(Node node) {
        children.add(node);
    }

    public int getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof Node) return id == ((Node) other).id;
        return false;
    }
}
