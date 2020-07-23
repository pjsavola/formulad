package gp.ai;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;


public final class Node {
    public enum Type { UNKNOWN, STRAIGHT, CURVE_2, CURVE_1, START, FINISH, CURVE_3, PIT };
    private final int id;
    private final Type type;
    private final Set<Node> nextNodes = new HashSet<>();
    private final Set<Node> adjacentNodes = new HashSet<>();

    public Node(int id, int type) {
        this(id, Type.values()[type]);
    }

    public Node(int id, Type type) {
        this.id = id;
        this.type = type;
    }

    public Node(gp.model.Node node) {
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
            adjacentNodes.remove(node);
            node.adjacentNodes.remove(this);
        }
    }

    /**
     * Used only in map editor and when loading nodes.
     */
    public void addAdjacentNode(Node node) {
        if (node != this && !nextNodes.contains(node) && !node.nextNodes.contains(this)) {
            adjacentNodes.add(node);
            node.adjacentNodes.remove(this); // prevent cycles of length 2
        }
    }

    /**
     * Used only by the map editor.
     */
    public boolean removeChild(Node node) {
        return nextNodes.remove(node);
    }

    /**
     * Used only by the map editor.
     */
    public boolean removeAdjacentNode(Node node) {
        return adjacentNodes.remove(node);
    }

    public void forEachChild(Consumer<Node> consumer) {
        nextNodes.forEach(consumer);
    }

    public void forEachAdjacentNode(Consumer<Node> consumer) {
        adjacentNodes.forEach(consumer);
    }

    public long childCount(Type excludeType) {
        return excludeType == null ? nextNodes.size() : nextNodes.stream().filter(n -> n.type != excludeType).count();
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

    public boolean isAdjacentOf(Node node) {
        return nextNodes.contains(node) || node.nextNodes.contains(this);
    }

    public boolean isInFrontOf(Node node) {
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
        return false;
    }

    public static Map<Node, Set<Node>> buildAdjacencyMap(List<Node> nodes, Map<Node, List<Node>> prevNodeMap) {
        final Map<Node, Set<Node>> adjacentNodes = new HashMap<>();
        nodes.forEach(node -> {
            node.forEachChild(next -> adjacentNodes.computeIfAbsent(node, _key -> new HashSet<>()).add(next));
            node.forEachAdjacentNode(next -> adjacentNodes.computeIfAbsent(node, _key -> new HashSet<>()).add(next));
            prevNodeMap.get(node).forEach(prev -> adjacentNodes.computeIfAbsent(node, _key -> new HashSet<>()).add(prev));
        });
        for (Map.Entry<Node, Set<Node>> e : adjacentNodes.entrySet()) {
            final Node node = e.getKey();
            e.getValue().removeIf(node::isInFrontOf);
            e.getValue().removeIf(neighbor -> neighbor.type == Type.PIT && node.type == Type.PIT);
        }
        return adjacentNodes;
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
