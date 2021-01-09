package gp.ai;

import gp.TrackLanes;

import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;


public final class Node implements Serializable, Comparable<Node> {
    private final int id;
    private final NodeType type;
    private transient Set<Node> nextNodes = new HashSet<>();
    private boolean garage;
    private boolean finish;
    private double distance = -1.0;
    private transient int stepsToFinishLine = -1;
    private transient int areaIndex;
    private transient double gridAngle = Double.NaN; // Client does not need this
    private Point point;

    public Node(int id, NodeType type) {
        this.id = id;
        if (type == NodeType.FINISH) {
            this.type = NodeType.STRAIGHT;
            finish = true;
        } else {
            this.type = type;
        }
    }

    public void setGarage(boolean garage) {
        this.garage = garage;
    }

    public void setFinish(boolean finish) {
        this.finish = finish;
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

    void setStepsToFinishLine(int steps) {
        stepsToFinishLine = steps;
    }

    void setAreaIndex(int index) {
        areaIndex = index;
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

    public boolean hasFinish() {
        return finish;
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

    int getStepsToFinishLine() {
        return stepsToFinishLine;
    }

    int getAreaIndex() {
        return areaIndex;
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

    public void removeChild(Node node) {
        nextNodes.remove(node);
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

    public Stream<Node> childStream() {
        return nextNodes.stream();
    }

    public boolean isPit() {
        return type == NodeType.PIT;
    }

    /**
     * Returns minimum distance to the next area for which isCurve() returns a different
     * value, does not take obstacles into account.
     */
    public int getMinDistanceToNextArea() {
        final boolean startNodeIsCurve = isCurve();
        final boolean inPits = isPit();
        final List<Node> work = new ArrayList<>();
        final Map<Node, Integer> visited = new HashMap<>();
        work.add(this);
        visited.put(this, 0);
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            for (Node next : node.nextNodes) {
                if (next.isPit() && !inPits) {
                    continue;
                }
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
        return type == NodeType.CURVE_1 || type == NodeType.CURVE_2 || type == NodeType.CURVE_3 || (type == NodeType.BLOCKED && !nextNodes.isEmpty() && nextNodes.iterator().next().isCurve());
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

    @Override
    public int compareTo(Node node) {
        if (distance == node.distance) {
            if (isCurve() && !node.isCurve()) {
                return 1;
            } else if (!isCurve() && node.isCurve()) {
                return -1;
            }
            final int distanceToNextArea1 = getMinDistanceToNextArea();
            final int distanceToNextArea2 = node.getMinDistanceToNextArea();
            final int delta = distanceToNextArea1 - distanceToNextArea2;
            return isCurve() ? delta : -delta;
        }
        return TrackLanes.distanceToInt(node.distance - distance);
    }

    @Override
    public String toString() {
        return id + " " + type.toString();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        nextNodes = new HashSet<>();
    }
}
