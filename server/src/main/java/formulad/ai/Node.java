package formulad.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;


public final class Node {
    public enum Type { UNKNOWN, STRAIGHT, CURVE_2, CURVE_1, START, FINISH, CURVE_3 };
    private final int id;
    private final Type type;
    private final Set<Node> nextNodes = new HashSet<>();

    public Node(int id, int type) {
        this(id, Type.values()[type]);
    }

    public Node(int id, Type type) {
        this.id = id;
        this.type = type;
    }

    public Node(formulad.model.Node node) {
        this.id = node.getNodeId();
        this.type = Type.valueOf(node.getType().name());
    }

    public int getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    /**
     * Used only in map editor and when loading nodes.
     */
    public void addChild(Node node) {
        if (node != this) {
            nextNodes.add(node);
            node.nextNodes.remove(this); // prevent cycles of length 2
        }
    }

    /**
     * Used only by the map editor.
     */
    public boolean removeChild(Node node) {
        return nextNodes.remove(node);
    }

    public void forEachChild(Consumer<Node> consumer) {
        nextNodes.forEach(consumer);
    }

    public int childCount() {
        return nextNodes.size();
    }

    public boolean hasChild(Node node) {
        return nextNodes.contains(node);
    }

    public boolean hasChildren(Collection<Node> node) {
        return nextNodes.containsAll(node);
    }

    public Stream<Node> childStream() {
        return nextNodes.stream();
    }

    /**
     * Returns true if this node is close enough to the given node to cause
     * possible collision damage if cars are in these two nodes.
     */
    public boolean isCloseTo(Node node, Map<Node, List<Node>> prevNodeMap) {
        if (isAdjacentOf(node)) return !isInFrontOf(node);

        // Nodes can still be close to each other if there's no link between them.
        // One generic rule for this would be that if they have common parent or common child,
        // then the nodes are close to each other, unless common parent is parent of the common child.
        Node commonParent = null;
        for (Node previousNode : prevNodeMap.get(this)) {
            if (prevNodeMap.get(node).contains(previousNode)) {
                commonParent = previousNode;
                break;
            }
        }
        Node commonChild = null;
        for (Node nextNode : nextNodes) {
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

    public boolean isAdjacentOf(Node node) {
        return nextNodes.contains(node) || node.nextNodes.contains(this);
    }

    // NOTE: Currently returns false for curve nodes which are in front of another curve node with 2 exits,
    // but only 1 path between the nodes. This does not matter if this method is used only for adjacency checks
    // as then the node in front would be unreachaable.
    public boolean isInFrontOf(Node node) {
        if (node.nextNodes.contains(this)) {
            if (node.nextNodes.size() == 1) {
                return true;
            }
            // If there's a path of length 2 to this node, this node must be in front of the given node.
            for (Node next : node.nextNodes) {
                if (next != this) {
                    for (Node nextOfNext : next.nextNodes) {
                        if (nextOfNext == this) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns minimum distance to the next area for which isCurve() returns a different
     * value, does not take obstacles into account.
     */
    public int getDistanceToNextArea() {
        final boolean startNodeIsCurve = isCurve();
        final List<Node> work = new ArrayList<>();
        final Map<Node, Integer> visited = new HashMap<>();
        work.add(this);
        visited.put(this, 0);
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            for (Node next : node.nextNodes) {
                if (next.isCurve() == !startNodeIsCurve) {
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
        return type == Type.CURVE_1 || type == Type.CURVE_2 || type == Type.CURVE_3;
    }

    public int getStopCount() {
        switch (type) {
            case CURVE_1: return 1;
            case CURVE_2: return 2;
            case CURVE_3: return 3;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        return id + 1;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof Node) return id == ((Node) other).id;
        return false;
    }
}
