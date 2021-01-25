package gp.ai;

import gp.ImageCache;
import gp.Main;
import gp.MapEditor;
import gp.TrackLanes;
import gp.model.Weather;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class TrackData implements Serializable {
    private final String trackId;
    private final boolean external;
    private final List<Node> nodes;
    private final List<Integer> sources = new ArrayList<>();
    private final List<Integer> targets = new ArrayList<>();
    private final ImageData imageData;
    private final MapEditor.Corner infoBoxCorner;
    public transient final Weather.Params weatherParams;
    private transient List<Node> startingGrid; // client does not need this
    private transient Map<Node, Set<Node>> collisionMap; // client does not need this

    private TrackData(String trackId, boolean external, List<Node> nodes, List<Node> startingGrid, Map<Node, Set<Node>> collisionMap, String imageFile, MapEditor.Corner infoBoxCorner, Weather.Params params) {
        this.trackId = trackId;
        this.external = external;
        this.nodes = nodes.stream().sorted(Comparator.comparingInt(Node::getId)).collect(Collectors.toList());
        nodes.stream().filter(node -> node.getType() == NodeType.BLOCKED).forEach(blockedNode -> {
            nodes.forEach(node -> node.removeChild(blockedNode));
            if (blockedNode.childCount(null) == 0) {
                throw new RuntimeException("Track seems to be completely blocked");
            }
        });
        nodes.forEach(node -> node.forEachChild(child -> {
            sources.add(node.getId());
            targets.add(child.getId());
        }));
        this.startingGrid = startingGrid;
        this.collisionMap = collisionMap;
        imageData = imageFile == null ? null : new ImageData(imageFile, external);
        this.infoBoxCorner = infoBoxCorner;
        this.weatherParams = params;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        for (int i = 0; i < sources.size(); ++i) {
            int src = sources.get(i);
            int dst = targets.get(i);
            nodes.get(src).addChild(nodes.get(dst));
        }
    }

    public static TrackData createTrackData(String trackId, boolean external) {
        if (external && !new File(trackId).exists()) {
            return null;
        }
        final List<Node> nodes = new ArrayList<>();
        final Map<Node, Double> attributes = new HashMap<>();
        final Weather.Params params = new Weather.Params();
        try (InputStream is = external ? new FileInputStream(trackId) : Main.class.getResourceAsStream("/" + trackId)) {
            final Pair<String, MapEditor.Corner> result = MapEditor.loadNodes(is, nodes, attributes, params);
            if (result == null) {
                return null;
            }
            final List<Node> grid = new ArrayList<>(10);
            final int laneCount = build(nodes, attributes, grid);
            if (grid.size() < Main.minGridSize) {
                return null;
            }
            /*
            if (external && !new File(imageFile).exists()) {
                return null;
            }
            final BufferedImage image = external ? ImageCache.getImageFromPath(imageFile) : ImageCache.getImage("/" + imageFile);
            if (image == null) {
                return null;
            }*/
            final Map<Node, Set<Node>> collisionMap = TrackLanes.buildCollisionMap(nodes, laneCount);
            return new TrackData(trackId, external, nodes, grid, collisionMap, result.getLeft(), result.getRight(), params);
        } catch (Exception e) {
            return null;
        }
    }

    private static int processFinishLine(List<Node> nodes, Map<Node, List<Node>> prevNodeMap, Deque<Node> work, Deque<Node> curves) {
        final List<Node> edges = nodes.stream().filter(Node::hasFinish).collect(Collectors.toList());
        final int laneCount = edges.size();
        if (laneCount < 3 || laneCount > 4) {
            throw new RuntimeException("Invalid finish line width " + laneCount);
        }
        final Set<Node> children = new HashSet<>();
        edges.forEach(n -> n.childStream().filter(Node::hasFinish).forEach(children::add));
        if (laneCount == 4) {
            if (children.size() != 2) {
                throw new RuntimeException("Malformed Finish line of width 4");
            }
            edges.stream().filter(n -> !children.contains(n)).forEach(n -> {
                n.setDistance(0.0);
                work.addLast(n);
            });
        } else if (children.size() == 1) {
            final Node center = children.iterator().next();
            edges.remove(center);
            center.setDistance(0.5);
            edges.get(0).setDistance(0.0);
            edges.get(1).setDistance(0.0);
            work.addLast(center);

            // Handle nasty special case where finish line is just before a curve
            if (center.childCount(null) == 1) {
                edges.forEach(edge -> edge.childStream().filter(n -> n != center).filter(n -> !center.hasChild(n)).forEach(n -> {
                    if (n.isCurve()) {
                        curves.addLast(n);
                    } else {
                        n.setDistance(1.0);
                        work.addLast(n);
                    }
                }));
            }
        } else if (children.size() == 2) {
            final Node center = edges.stream().filter(n -> !children.contains(n)).findAny().orElse(null);
            if (center == null) {
                throw new RuntimeException("Malformed Finish line");
            }
            center.setDistance(0.0);
            work.addLast(center);
        } else if (edges.stream().filter(Node::isCurve).count() == 1) {
            edges.forEach(n -> children.addAll(prevNodeMap.get(n)));
            final Optional<Node> candidate = edges.stream().filter(n -> children.stream().allMatch(prev -> prev.hasChild(n))).findAny();
            if (candidate.isPresent()) {
                final Node center = candidate.get();
                edges.remove(center);
                center.setDistance(0.0);
                work.addLast(center);

                if (edges.get(0).isCurve()) {
                    curves.addLast(edges.get(0));
                    edges.get(1).setDistance(0.5);
                    work.addLast(edges.get(1));
                } else {
                    curves.addLast(edges.get(1));
                    edges.get(0).setDistance(0.5);
                    work.addLast(edges.get(0));
                }
            } else {
                throw new RuntimeException("Finish line seems to be disjoint");
            }
        } else {
            throw new RuntimeException("Finish line seems to be disjoint");
        }
        return laneCount;
    }

    public static int updateDistances(List<Node> nodes, Map<Node, Double> attributes) {
        for (Node node : nodes) {
            node.setDistance(-1.0);
            if (node.getType() == NodeType.BLOCKED) {
                // Temporarily set this distance for blocked nodes to avoid visiting them later.
                node.setDistance(0.0);
            }
        }
        final Map<Node, List<Node>> prevNodeMap = AIUtil.buildPrevNodeMap(nodes);
        final Deque<Node> work = new ArrayDeque<>();
        final Deque<Node> curves = new ArrayDeque<>();
        final int laneCount = processFinishLine(nodes, prevNodeMap, work, curves);

        int areaIndex = 0;
        final MutableObject<Node> pit = new MutableObject<>(null);

        while (!work.isEmpty()) {
            final Node node = work.removeFirst();
            if (node.childCount(NodeType.PIT) == 0) {
                throw new RuntimeException("Found dead-end node: " + node.getId());
            }
            node.setAreaIndex(areaIndex);

            final Map<Integer, Set<Node>> depthToAncestors = new HashMap<>();
            int depth = 1;
            Set<Node> currentAncestors = node.childStream().filter(child -> !child.isPit()).collect(Collectors.toSet());
            while (depth < 5) {
                depthToAncestors.put(depth, currentAncestors);
                final Set<Node> nextAncestors = new HashSet<>();
                currentAncestors.forEach(ancestor -> nextAncestors.addAll(ancestor.childStream().filter(child -> !child.isPit()).filter(child -> prevNodeMap.get(child).size() != 1).collect(Collectors.toSet())));
                currentAncestors = nextAncestors;
                depth++;
            }
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
                double distanceDelta = 0.5;
                if (prevNodeMap.get(next).stream().filter(n -> !n.isPit()).count() == 1) {
                    // We might end up here in 2 cases:
                    // - Just before curve where movement is limited
                    // - In case lane lengths are different
                    distanceDelta = next.childStream().anyMatch(Node::isCurve) ? 1.0 : 0.4;
                } else {
                    for (int i = 4; i > 0; --i) {
                        if (depthToAncestors.get(i).contains(next)) {
                            distanceDelta = 0.5 * i;
                            break;
                        }
                    }
                }
                next.setDistance(node.getDistance() + distanceDelta);
                work.addLast(next);
            });
            if (work.isEmpty() && !curves.isEmpty()) {
                ++areaIndex;
                final double maxDistance = nodes.stream().map(Node::getDistance).mapToDouble(Double::doubleValue).max().orElse(0) + (3 - curves.stream().distinct().count()) * 0.5;
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
                final double newMaxDistance = nodes.stream().map(Node::getDistance).mapToDouble(Double::doubleValue).max().orElse(0) + (3 - work.stream().distinct().count()) * 0.5;
                Node center = null;
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
                        final boolean middle = straight.childCount(NodeType.PIT) == 3;
                        if (middle) {
                            straight.setAreaIndex(areaIndex);
                            center = straight;
                            break;
                        }
                        straight.setDistance(newMaxDistance);
                        straight.setAreaIndex(areaIndex);
                        for (Node otherStraight : work) {
                            if (straight.hasChild(otherStraight)) {
                                center = otherStraight;
                                break;
                            }
                        }
                        straight.childStream().filter(Node::isPit).findAny().ifPresent(pitNode -> {
                            if (pitNode.getDistance() <= 0.0) {
                                pit.setValue(pitNode);
                                pitNode.setDistance(newMaxDistance - 0.4);
                            }
                        });
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
            prevNodeMap.get(pitEntry).stream().map(Node::getDistance).max(Double::compareTo).ifPresent(max -> pitEntry.setDistance(max + 0.1));
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
        nodes.stream().filter(node -> node.getType() == NodeType.BLOCKED).forEach(blockedNode -> {
            final OptionalDouble distanceOfNext = blockedNode.childStream().mapToDouble(n -> prevNodeMap.get(n).get(0).getDistance()).min();
            if (distanceOfNext.isPresent()) {
                blockedNode.setDistance(distanceOfNext.getAsDouble() - 0.01);
            }
        });
        nodes.stream().filter(Node::isCurve).filter(n -> n.childCount(NodeType.PIT) == 2).forEach(n -> {
            if (n.childStream().mapToDouble(Node::getDistance).distinct().count() < 2) {
                throw new RuntimeException("Identical distances for child nodes, unable to deduce lanes: " + n.getId());
            }
        });
        return laneCount;
    }

    public static int build(List<Node> nodes, Map<Node, Double> attributes, List<Node> grid) {
        final int laneCount = updateDistances(nodes, attributes);
        nodes.stream().filter(n -> n.getDistance() < 0.0).findAny().ifPresent(n -> {
            throw new RuntimeException("Track contains unreachable node: " + n.getId() + " (" + n.getLocation().x + "," + n.getLocation().y + ")");
        });
        nodes.forEach(node -> {
            final boolean isPit = node.getType() == NodeType.PIT;
            final double distance = node.getDistance();
            node.forEachChild(next -> {
                if (next.hasFinish()) {
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
        if (grid != null) {
            nodes.stream()
                    .filter(node -> !Double.isNaN(node.getGridAngle()))
                    .sorted((n1, n2) -> TrackLanes.distanceToInt(n2.getDistance()) - TrackLanes.distanceToInt(n1.getDistance()))
                    .forEach(grid::add);
        }
        return laneCount;
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

    public int getGridMaxSize() {
        return startingGrid.size();
    }

    public List<Node> getStartingGrid(int playerCount) {
        return startingGrid.subList(0, playerCount);
    }

    public Map<Node, Set<Node>> getCollisionMap() {
        return collisionMap;
    }

    public BufferedImage getBackgroundImage() {
        return imageData == null ? null : imageData.getImage();
    }

    public String getCacheKey() {
        return imageData == null ? null : ((imageData.external ? "/" : "") + imageData.imagePath);
    }

    public MapEditor.Corner getInfoBoxCorner() {
        return infoBoxCorner;
    }

    private static class ImageData implements Serializable {
        private transient BufferedImage image;
        private final transient String imagePath;
        private final transient boolean external;

        private ImageData(String imagePath, boolean external) {
            this.imagePath = imagePath;
            this.external = external;
        }

        private BufferedImage getImage() {
            if (image == null) {
                try {
                    image = external ? ImageCache.getImageFromPath(imagePath) : ImageCache.getImage("/" + imagePath);
                } catch (Exception e) {
                    // Missing image
                }
            }
            return image;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            final BufferedImage image = getImage();
            if (image != null) {
                ImageIO.write(image, "png", out); // png is lossless
            }
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
