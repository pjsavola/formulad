package gp.ai;

import gp.TrackLanes;

import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;


public final class Node implements Serializable {
    private final int id;
    private final NodeType type;
    private final Set<Node> nextNodes = new HashSet<>();
    private boolean garage;
    private double distance = -1.0;
    private transient double gridAngle = Double.NaN; // Client does not need this
    private Point point;

    public Node(int id, NodeType type) {
        this.id = id;
        this.type = type;
    }

    public Node(gp.model.Node node) {
        id = node.getNodeId();
        type = NodeType.valueOf(node.getType().getValue());
    }

    public void setGarage(boolean garage) {
        this.garage = garage;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public void setGridAngle(double gridAngle) {
        this.gridAngle = gridAngle;
    }

    public void setLocation(Point point) {
        this.point = point;
    }

    public int getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public boolean hasGarage() {
        return garage;
    }

    public double getDistance() {
        return distance;
    }

    public double getGridAngle() {
        return gridAngle;
    }

    public Point getLocation() {
        return point;
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

    public long childCount(NodeType excludeType) {
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
        return type == NodeType.CURVE_1 || type == NodeType.CURVE_2 || type == NodeType.CURVE_3;
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

    public int compareTo(Node node) {
        if (distance == node.distance) {
            if (isCurve() && !node.isCurve()) {
                return 1;
            } else if (!isCurve() && node.isCurve()) {
                return -1;
            }
            final int distanceToNextArea1 = getDistanceToNextArea();
            final int distanceToNextArea2 = node.getDistanceToNextArea();
            final int delta = distanceToNextArea1 - distanceToNextArea2;
            return isCurve() ? delta : -delta;
        }
        return TrackLanes.distanceToInt(node.distance - distance);
    }
}
