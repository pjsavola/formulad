package formulad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    public boolean isCloseTo(final Node node, final Map<Node, List<Node>> prevNodeMap) {
        boolean adjacent = isAdjacentOf(node);
        if (adjacent) return !isInFrontOf(node);
        // Nodes can still be close to each other if there's no link between them.
        // One generic rule for this would be that if they have common parent or common child,
        // then the nodes are close to each other, unless common parent is parent of the common child.
        Node commonParent = null;
        for (final Node previousNode : prevNodeMap.get(this)) {
            if (prevNodeMap.get(node).contains(previousNode)) {
                commonParent = previousNode;
                break;
            }
        }
        Node commonChild = null;
        for (final Node nextNode : nextNodes) {
            if (node.nextNodes.contains(nextNode)) {
                commonChild = nextNode;
                break;
            }
        }
        if (commonParent != null && commonChild != null) {
            return !commonParent.nextNodes.contains(commonChild);
        }
        return commonParent != null || commonChild != null;
    }

    public boolean isAdjacentOf(final Node node) {
        return nextNodes.contains(node) || node.nextNodes.contains(this);
    }

    // NOTE: Currently returns false for curve nodes which are in front of another curve node with 2 exits,
    // but only 1 path between the nodes. This does not matter if this method is used only for adjacency checks
    // as then the node in front would be unreachaable.
    public boolean isInFrontOf(final Node node) {
        if (node.nextNodes.contains(this)) {
            if (node.nextNodes.size() == 1) {
                return true;
            }
            // If there's a path of length 2 to this node, this node must be in front of the given node.
            for (final Node next : node.nextNodes) {
                if (next != this) {
                    for (final Node nextOfNext : next.nextNodes) {
                        if (nextOfNext == this) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public int getDistanceToNextArea(final boolean curve) {
        final List<Node> work = new ArrayList<>();
        final Map<Node, Integer> visited = new HashMap<>();
        work.add(this);
        visited.put(this, 0);
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            for (final Node next : node.nextNodes) {
                if (next.isCurve() == curve) {
                    return visited.get(node) + 1;
                }
                if (!visited.containsKey(next)) {
                    work.add(next);
                    visited.put(next, visited.get(node) + 1);
                }
            }
        }
        throw new RuntimeException("Next area not found!");
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
