package gp.ai;

import gp.ImageCache;
import gp.Main;
import gp.MapEditor;
import gp.TrackLanes;
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
        final Map<Node, Double> gridAngleMap = new HashMap<>();
        final Map<Node, Double> distanceMap = new HashMap<>();
        final Map<Node, Point> coordinates = new HashMap<>();
        try (InputStream is = external ? new FileInputStream(trackId) : Main.class.getResourceAsStream("/" + trackId)) {
            final Pair<String, MapEditor.Corner> result = MapEditor.loadNodes(is, nodes, attributes, gridAngleMap, coordinates);
            if (result == null) {
                return null;
            }
            final Map<Node, List<Node>> prevNodeMap = AIUtil.buildPrevNodeMap(nodes);
            final List<Node> grid = build(nodes, attributes, gridAngleMap, distanceMap, prevNodeMap);
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
            attributes.forEach((node, attr) -> {
                if (node.getType() == NodeType.PIT && attr != null) {
                    node.setGarage(true);
                }
            });
            gridAngleMap.forEach(Node::setGridAngle);
            distanceMap.forEach(Node::setDistance);
            coordinates.forEach(Node::setLocation);
            final Map<Node, Set<Node>> collisionMap = TrackLanes.buildCollisionMap(nodes, distanceMap);
            return new TrackData(trackId, external, nodes, grid, collisionMap, image, result.getRight());
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Node> build(List<Node> nodes, Map<Node, Double> attributes, Map<Node, Double> gridAngles, Map<Node, Double> distanceMap, Map<Node, List<Node>> prevNodeMap) {
	    final Set<Node> visited = new HashSet<>();
        final List<Node> work = new ArrayList<>();
        final List<Node> edges = new ArrayList<>();
        Node center = null;
        for (Node node : nodes) {
            if (node.getType() == NodeType.FINISH) {
                work.add(node);
                visited.add(node);
                if (node.childCount(NodeType.PIT) == 3) {
                    center = node;
                } else {
                    edges.add(node);
                }
            }
            if (node.isCurve() && !attributes.containsKey(node)) {
                throw new RuntimeException("There is a curve without distance attribute");
            }
            if (node.childCount(null) == 0) {
                throw new RuntimeException("Track contains a dead-end");
            }
        }
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            node.forEachChild(next -> {
                if (visited.add(next)) {
                    work.add(next);
                }
            });
        }
        if (nodes.size() != visited.size()) {
            throw new RuntimeException("Track contains unreachable nodes");
        }
        if (center == null) {
            throw new RuntimeException("Finish line must have width 3");
        }
        if (center.hasChildren(edges)) {
            distanceMap.put(center, 0.0);
        } else {
            distanceMap.put(center, 0.5);
            distanceMap.put(edges.get(0), 0.0);
            distanceMap.put(edges.get(1), 0.0);
        }
        work.add(center);
        final MutableObject<Node> pit = new MutableObject<>(null);
        final List<Node> curves = new ArrayList<>();
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            final long childCount = node.childCount(NodeType.PIT);
            node.forEachChild(next -> {
                if (distanceMap.containsKey(next)) {
                    return;
                }
                if (next.isCurve()) {
                    curves.add(next);
                    return;
                }
                if (next.getType() == NodeType.PIT) {
                    pit.setValue(next);
                    distanceMap.put(next, distanceMap.get(node) - 0.4);
                    return;
                }
                final long nextChildCount = next.childCount(NodeType.PIT);
                final boolean fromCenterToEdge = childCount == 3 && (nextChildCount == 2 || prevNodeMap.get(next).stream().anyMatch(Node::isCurve));
                distanceMap.put(next, distanceMap.get(node) + (fromCenterToEdge ? 0.5 : 1));
                work.add(next);
            });
            if (work.isEmpty() && !curves.isEmpty()) {
                final double maxDistance = distanceMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
                for (Node curve : curves) {
                    final double relativeDistance = attributes.get(curve);
                    distanceMap.put(curve, maxDistance + relativeDistance);
                }
                while (!curves.isEmpty()) {
                    final Node curve = curves.remove(0);
                    curve.forEachChild(next -> {
                        if (distanceMap.containsKey(next)) {
                            return;
                        }
                        if (!next.isCurve()) {
                            work.add(next);
                            return;
                        }
                        curves.add(next);
                        distanceMap.put(next, attributes.get(next) + maxDistance);
                    });
                }
                final double newMaxDistance = distanceMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
                center = null;
                if (work.isEmpty()) {
                    throw new RuntimeException("Curve exit must have size > 0");
                }
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
                        distanceMap.put(straight, newMaxDistance);
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
                    StringBuilder sb = new StringBuilder();
                    work.stream().distinct().forEach(n -> sb.append(" ").append(n.getId()));
                    throw new RuntimeException("Nodes" + sb.toString() + " might be missing edges");
                }
                work.clear();
                work.add(center);
                distanceMap.put(center, newMaxDistance + 0.5);
            }
        }
        final Node pitEntry = pit.getValue();
        if (pitEntry != null) {
            prevNodeMap.get(pitEntry).stream().map(distanceMap::get).min(Double::compareTo).ifPresent(min -> distanceMap.put(pitEntry, min - 0.4));
        }
        while (pit.getValue() != null) {
            final Node node = pit.getValue();
            final long childCount = node.childCount(null);
            if (childCount > 1) {
                node.forEachChild(next -> {
                    if (next.getType() == NodeType.PIT || !distanceMap.containsKey(next)) {
                        throw new RuntimeException("Pit lane is branching");
                    }
                });
                pit.setValue(null);
            } else if (childCount < 1) {
                throw new RuntimeException("Pit lane is dead end");
            } else {
                node.forEachChild(next -> {
                    distanceMap.put(next, distanceMap.get(node) + 0.01);
                    pit.setValue(next);
                });
            }
        }
        final List<Node> grid = new ArrayList<>(gridAngles.keySet());
        grid.sort((n1, n2) -> TrackLanes.distanceToInt(distanceMap.get(n2)) - TrackLanes.distanceToInt(distanceMap.get(n1)));

        nodes.forEach(node -> {
            final boolean isPit = node.getType() == NodeType.PIT;
            final double distance = distanceMap.get(node);
            node.forEachChild(next -> {
                if (next.getType() == NodeType.FINISH) {
                    return;
                }
                final boolean nextIsPit = next.getType() == NodeType.PIT;
                if (isPit && !nextIsPit) return;
                if (nextIsPit && !isPit) return;
                final double childDistance = distanceMap.get(next);
                if (childDistance <= distance) {
                    throw new RuntimeException("Track might contain a cycle: " + node.getId() + " -> " + next.getId());
                }
            });
        });
        return grid;
    }

    public String getTrackId() {
        return trackId;
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
