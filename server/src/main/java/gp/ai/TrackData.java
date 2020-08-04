package gp.ai;

import gp.ImageCache;
import gp.Main;
import gp.MapEditor;
import gp.TrackLanes;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TrackData implements Serializable {
    private final String trackId;
    private final boolean external;
    private final List<Node> nodes;
    private final ImageData backgroundImage;
    private final MapEditor.Corner infoBoxCorner;
    private transient List<Node> startingGrid; // client does not need this
    private transient Map<Node, Set<Node>> collisionMap; // client does not need this

    private TrackData(String trackId, boolean external, List<Node> nodes, List<Node> startingGrid, Map<Node, Set<Node>> collisionMap, BufferedImage backgroundImage, MapEditor.Corner infoBoxCorner) {
        this.trackId = trackId;
        this.external = external;
        this.nodes = nodes.stream().sorted(Comparator.comparingInt(Node::getId)).collect(Collectors.toList());
        this.startingGrid = startingGrid;
        this.collisionMap = collisionMap;
        this.backgroundImage = backgroundImage == null ? null : new ImageData(backgroundImage);
        this.infoBoxCorner = infoBoxCorner;
    }

    public static TrackData createTrackData(String trackId, boolean external) {
        if (external && !new File(trackId).exists()) {
            return null;
        }
        final List<Node> nodes = new ArrayList<>();
        final Map<Node, Double> attributes = new HashMap<>();
        try (InputStream is = external ? new FileInputStream(trackId) : Main.class.getResourceAsStream("/" + trackId)) {
            final Pair<String, MapEditor.Corner> result = MapEditor.loadNodes(is, nodes, attributes);
            if (result == null) {
                return null;
            }
            final List<Node> grid = build(nodes, attributes);
            if (grid.size() < 10) {
                return null;
            }
            final String imageFile = result.getLeft();
            if (external && !new File(imageFile).exists()) {
                return null;
            }
            final BufferedImage image = external ? ImageCache.getImageFromPath(imageFile) : ImageCache.getImage("/" + imageFile);
            if (image == null) {
                return null;
            }
            final Map<Node, Set<Node>> collisionMap = TrackLanes.buildCollisionMap(nodes);
            return new TrackData(trackId, external, nodes, grid, collisionMap, image, result.getRight());
        } catch (Exception e) {
            return null;
        }
    }

    public static void updateDistances(List<Node> nodes, Map<Node, Double> attributes) {
        final List<Node> edges = new ArrayList<>();
        Node center = null;
        for (Node node : nodes) {
            if (node.getType() == NodeType.FINISH) {
                if (node.childCount(NodeType.PIT) == 3) {
                    center = node;
                } else {
                    edges.add(node);
                }
            }
            node.setDistance(-1.0);
        }
        if (center == null || edges.size() != 2) {
            throw new RuntimeException("Unable to find Finish line of width 3");
        }
        if (center.hasChildren(edges)) {
            center.setDistance(0.0);
        } else if (edges.get(0).hasChild(center) && edges.get(1).hasChild(center)) {
            center.setDistance(0.5);
            edges.get(0).setDistance(0.0);
            edges.get(1).setDistance(0.0);
        } else {
            throw new RuntimeException("Finish line seems to be disjoint");
        }
        int areaIndex = 0;
        final Map<Node, List<Node>> prevNodeMap = AIUtil.buildPrevNodeMap(nodes);
        final Deque<Node> work = new ArrayDeque<>();
        work.addLast(center);
        final MutableObject<Node> pit = new MutableObject<>(null);
        final Deque<Node> curves = new ArrayDeque<>();
        while (!work.isEmpty()) {
            final Node node = work.removeFirst();
            if (node.childCount(NodeType.PIT) == 0) {
                throw new RuntimeException("Found dead-end node: " + node.getId());
            }
            node.setAreaIndex(areaIndex);
            final Set<Node> grandChildren = new HashSet<>();
            node.forEachChild(child -> {
                if (child.getType() == NodeType.PIT) return;
                child.forEachChild(grandChild -> {
                    if (grandChild.getType() == NodeType.PIT) return;
                    grandChildren.add(grandChild);
                });
            });
            node.forEachChild(next -> {
                if (next.getDistance() >= 0.0) {
                    return;
                }
                if (next.isCurve()) {
                    curves.addLast(next);
                    return;
                }
                if (next.getType() == NodeType.PIT) {
                    pit.setValue(next);
                    next.setDistance(node.getDistance() - 0.4);
                    return;
                }
                final double distanceDelta;
                if (prevNodeMap.get(next).size() == 1) {
                    // We might end up here in 2 cases:
                    // - Just before curve where movement is limited
                    // - In case lane lengths are different
                    distanceDelta = next.childStream().anyMatch(Node::isCurve) ? 1.0 : 0.4;
                } else if (grandChildren.contains(next)) {
                    distanceDelta = 1.0;
                } else {
                    distanceDelta = 0.5;
                }
                next.setDistance(node.getDistance() + distanceDelta);
                work.addLast(next);
            });
            if (work.isEmpty() && !curves.isEmpty()) {
                ++areaIndex;
                final double maxDistance = nodes.stream().map(Node::getDistance).mapToDouble(Double::doubleValue).max().orElse(0);
                while (!curves.isEmpty()) {
                    final Node curve = curves.removeFirst();
                    final Double relativeDistance = attributes.get(curve);
                    if (relativeDistance == null) {
                        throw new RuntimeException("Found curve without distance attribute: " + curve.getId());
                    }
                    curve.setAreaIndex(areaIndex);
                    curve.setDistance(maxDistance + relativeDistance);
                    if (curve.childCount(NodeType.PIT) == 0) {
                        throw new RuntimeException("Found dead-end curve: " + curve.getId());
                    }
                    curve.forEachChild(next -> {
                        if (next.getDistance() >= 0.0) {
                            return;
                        }
                        if (!next.isCurve()) {
                            work.add(next);
                            return;
                        }
                        curves.addLast(next);
                    });
                }
                final double newMaxDistance = nodes.stream().map(Node::getDistance).mapToDouble(Double::doubleValue).max().orElse(0);
                center = null;
                if (work.isEmpty()) {
                    throw new RuntimeException("Track cannot end in a curve");
                }
                ++areaIndex;
                for (Node straight : work) {
                    boolean allCurves = true;
                    for (Node prev : prevNodeMap.get(straight)) {
                        if (prev.getType() == NodeType.PIT) {
                            continue;
                        }
                        if (!prev.isCurve()) {
                            allCurves = false;
                            break;
                        }
                    }
                    if (allCurves) {
                        straight.setDistance(newMaxDistance);
                        straight.setAreaIndex(areaIndex);
                        for (Node otherStraight : work) {
                            if (straight.hasChild(otherStraight)) {
                                center = otherStraight;
                                break;
                            }
                        }
                        break;
                    }
                }
                if (center == null) {
                    final StringBuilder sb = new StringBuilder();
                    work.stream().distinct().forEach(n -> sb.append(" ").append(n.getId()));
                    throw new RuntimeException("Curve exit" + sb.toString() + " might be missing edges");
                }
                work.clear();
                center.setDistance(newMaxDistance + 0.5);
                work.addLast(center);
            }
        }
        ++areaIndex;
        final Node pitEntry = pit.getValue();
        if (pitEntry != null) {
            prevNodeMap.get(pitEntry).stream().map(Node::getDistance).min(Double::compareTo).ifPresent(min -> pitEntry.setDistance(min - 0.4));
        }
        while (pit.getValue() != null) {
            final Node node = pit.getValue();
            node.setAreaIndex(areaIndex);
            if (node.childStream().filter(n -> n.getType() != NodeType.PIT).anyMatch(n -> n.getDistance() < 0.0)) {
                throw new RuntimeException("Distance not defined at pit lane exit");
            }
            final long childCount = node.childStream().filter(n -> n.getType() == NodeType.PIT).count();
            if (childCount > 1) {
                throw new RuntimeException("Pit lane is branching");
            } else if (childCount == 0) {
                if (node.childCount(NodeType.PIT) == 0) {
                    throw new RuntimeException("Pit lane has dead-end: " + node.getId());
                }
                break;
            } else {
                node.forEachChild(next -> {
                    next.setDistance(node.getDistance() + 0.01);
                    pit.setValue(next);
                });
            }
        }
        work.clear();
        nodes.stream().filter(n -> n.getDistance() == 0.0).map(prevNodeMap::get).forEach(work::addAll);
        work.stream().distinct().forEach(n -> {
            n.setStepsToFinishLine(1);
            work.addAll(prevNodeMap.get(n));
        });
        while (!work.isEmpty()) {
            final Node node = work.removeFirst();
            if (node.isPit()) continue;
            if (node.getStepsToFinishLine() < 0) {
                int min = node.childStream().filter(n -> !n.isPit()).map(Node::getStepsToFinishLine).mapToInt(Integer::intValue).min().orElse(-1) + 1;
                if (min > 0) {
                    node.setStepsToFinishLine(min);
                    work.addAll(prevNodeMap.get(node));
                }
            }
        }
        if (pit.getValue() != null) {
            Node pitNode = pit.getValue();
            int stepsToPitLaneEnd = 1;
            while (pitNode != null) {
                pitNode.setStepsToFinishLine(stepsToPitLaneEnd++);
                pitNode = prevNodeMap.get(pitNode).stream().filter(Node::isPit).findAny().orElse(null);
            }
        }
    }

    public static List<Node> build(List<Node> nodes, Map<Node, Double> attributes) {
        updateDistances(nodes, attributes);
        nodes.stream().filter(n -> n.getDistance() < 0.0).findAny().ifPresent(n -> {
            throw new RuntimeException("Track contains unreachable node: " + n.getId());
        });
        final List<Node> grid = nodes
                .stream()
                .filter(node -> !Double.isNaN(node.getGridAngle()))
                .sorted((n1, n2) -> TrackLanes.distanceToInt(n2.getDistance()) - TrackLanes.distanceToInt(n1.getDistance()))
                .collect(Collectors.toList());
        nodes.forEach(node -> {
            final boolean isPit = node.getType() == NodeType.PIT;
            final double distance = node.getDistance();
            node.forEachChild(next -> {
                if (next.getType() == NodeType.FINISH) {
                    return;
                }
                final boolean nextIsPit = next.getType() == NodeType.PIT;
                if (isPit && !nextIsPit) return;
                if (nextIsPit && !isPit) return;
                final double childDistance = next.getDistance();
                if (childDistance <= distance) {
                    throw new RuntimeException("Distance does not increase when moving forwards, track might contain a cycle: " + node.getId() + " -> " + next.getId());
                }
            });
        });
        return grid;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getName() {
        return StringUtils.capitalize(trackId.substring(0, trackId.length() - 4));
    }

    public boolean isExternal() {
        return external;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Node> getStartingGrid(int playerCount) {
        return startingGrid.subList(0, playerCount);
    }

    public Map<Node, Set<Node>> getCollisionMap() {
        return collisionMap;
    }

    public BufferedImage getBackgroundImage() {
        return backgroundImage == null ? null : backgroundImage.image;
    }

    public MapEditor.Corner getInfoBoxCorner() {
        return infoBoxCorner;
    }

    private static class ImageData implements Serializable {
        private transient BufferedImage image;

        private ImageData(BufferedImage image) {
            this.image = image;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            ImageIO.write(image, "png", out); // png is lossless
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            image = ImageIO.read(in);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof TrackData) {
            final TrackData data = (TrackData) other;
            return trackId.equals(data.trackId) && external == data.external;
        }
        return false;
    }
}
