package formulad;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Node {
    public final int x;
    public final int y;
    public final int type;
    public final Set<Node> nextNodes = new HashSet<>();

    public Node(final int x, final int y, final int type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }

    public void addNext(final Node node) {
        if (node != this) {
            nextNodes.add(node);
            node.nextNodes.remove(this); // prevent cycles of length 2
        }
    }

    public boolean isCurve() {
        return type == MapEditor.CURVE_1 || type == MapEditor.CURVE_2;
    }

    public static Node getNode(final Collection<Node> nodes, final int x, final int y, int threshold) {
        for (final Node node : nodes) {
            if (Math.hypot(x - node.x, y - node.y) < threshold) {
                return node;
            }
        }
        return null;
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof Node) {
            final Node node = (Node) other;
            return type == node.type && x == node.x && y == node.y;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = type;
        result = 37 * result + x;
        result = 37 * result + y;
        return result;
    }
}
