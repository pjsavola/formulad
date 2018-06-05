package formulad;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.sun.istack.internal.Nullable;
import formulad.ai.AI;
import formulad.ai.DummyAI;
import formulad.ai.ManualAI;

public class Game extends JPanel implements Runnable {
    // Node identifier equals to the index in this array
    private final List<Node> nodes = new ArrayList<>();
    private final Map<Node, List<Node>> prevNodeMap = new HashMap<>();
    // Contains angles for start nodes and distance information for curves
    private Map<Node, Double> attributes = new HashMap<>();
    private BufferedImage backgroundImage;
    private LocalPlayer previous;
    private LocalPlayer current;
    private List<LocalPlayer> waitingPlayers = new ArrayList<>();
    private final List<LocalPlayer> players = new ArrayList<>();
    private final List<LocalPlayer> stoppedPlayers = new ArrayList<>();
    private final Map<Integer, AI> aiMap = new HashMap<>();
    private Integer roll;
    public static final Random r = new Random();
    private final Map<Node, Double> distanceMap = new HashMap<>();
    private boolean stopped;
    @Nullable
    private Map<Integer, Integer> highlightedNodeToDamage;

    public Game(JFrame frame) {
        backgroundImage = ImageCache.getImage("/Users/petrisavola/formulad/sebring.jpg");
        setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        final File file = new File("/Users/petrisavola/formulad/formulad.dat");
        MapEditor.loadNodes(file, nodes, attributes);
        for (final Node node : nodes) {
            for (final Node next : node.nextNodes) {
                prevNodeMap.computeIfAbsent(next, _node -> new ArrayList<>()).add(node);
            }
        }
        int edgeCount = 0;
        final int[][] nodeArray = new int[nodes.size()][2];
        final int[][] edgeArray = new int[nodes.stream().mapToInt(node -> node.nextNodes.size()).sum()][2];
        for (int i = 0; i < nodes.size(); i++) {
            final Node node = nodes.get(i);
            nodeArray[i][0] = i;
            nodeArray[i][1] = node.type;
            for (Node child : node.nextNodes) {
                edgeArray[edgeCount][0] = node.id;
                edgeArray[edgeCount][1] = child.id;
                edgeCount++;
            }
        }
        List<Node> grid = findGrid().subList(0, 10);
        for (int i = 0; i < grid.size(); i++) {
            final AI ai = new ManualAI(new DummyAI(), frame, this);
            final Node startNode = grid.get(i);
            final LocalPlayer player = new LocalPlayer(i, ai.getName(), startNode, attributes.get(startNode), 1);
            players.add(player);
            // Clone the array so nothing bad happens if AI mutates it
            ai.initialize(player.getId(), startNode.id, cloneArray(nodeArray), cloneArray(edgeArray));
            aiMap.put(player.getId(), ai);
        }
        waitingPlayers.addAll(players);
        current = waitingPlayers.remove(0);
    }

    @Override
    public void run() {
        while (!stopped) {
            // TODO: Measure time and timeout?
            final AI ai = aiMap.get(current.getId());
            final int selectedGear = ai.selectGear(getPlayerData());
            if (!current.switchGear(selectedGear)) {
                System.err.println("Invalid gear selection by player " + current.getId());
            }
            roll = current.roll();
            final int[][] allTargets = current.findAllTargets(roll, players);
            if (allTargets.length == 0) {
                current.stop();
            } else {
                int selectedIndex = ai.selectTarget(cloneArray(allTargets));
                if (selectedIndex < 0 || selectedIndex >= allTargets.length) {
                    System.err.println("Invalid target selection by player " + current.getId());
                    selectedIndex = 0;
                }
                current.move(allTargets[selectedIndex], selectedIndex);
                current.collide(players, prevNodeMap);
                if (roll == 20 || roll == 30) {
                    LocalPlayer.possiblyAddEngineDamage(players);
                }

            }
            roll = null;
            nextPlayer();
            repaint();
        }
        // TODO: Render standings!
        System.err.println(stoppedPlayers);
    }

    private static int[][] cloneArray(int[][] src) {
        int length = src.length;
        int[][] target = new int[length][src[0].length];
        for (int i = 0; i < length; i++) {
            System.arraycopy(src[i], 0, target[i], 0, src[i].length);
        }
        return target;
    }

    private int[][] getPlayerData() {
        final int[][] playerData = new int[players.size()][];
        for (int i = 0; i < players.size(); i++) {
            playerData[i] = players.get(i).getData();
        }
        return playerData;
    }

	private List<Node> findGrid() {
	    final Set<Node> visited = new HashSet<>();
        final List<Node> grid = new ArrayList<>();
        final List<Node> work = new ArrayList<>();
        final List<Node> edges = new ArrayList<>();
        Node center = null;
        for (final Node node : nodes) {
            if (node.type == MapEditor.FINISH) {
                work.add(node);
                visited.add(node);
                if (node.nextNodes.size() == 3) {
                    center = node;
                } else {
                    edges.add(node);
                }
            }
        }
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            if (node.type == MapEditor.START) {
                grid.add(0, node);
            }
            for (final Node next : node.nextNodes) {
                if (visited.add(next)) {
                    work.add(next);
                }
            }
        }
        if (center == null) {
            throw new RuntimeException("Finish line must have width 3");
        }
        if (center.nextNodes.containsAll(edges)) {
            distanceMap.put(center, 0.0);
        } else {
            distanceMap.put(center, 0.5);
            distanceMap.put(edges.get(0), 0.0);
            distanceMap.put(edges.get(1), 0.0);
        }
        work.add(center);
        final List<Node> curves = new ArrayList<>();
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            final int childCount = node.nextNodes.size();
            for (final Node next : node.nextNodes) {
                if (distanceMap.containsKey(next)) {
                    continue;
                }
                if (next.isCurve()) {
                    curves.add(next);
                    continue;
                }
                final int nextChildCount = next.nextNodes.size();
                final boolean fromCenterToEdge = childCount == 3 && nextChildCount == 2;
                distanceMap.put(next, distanceMap.get(node) + (fromCenterToEdge ? 0.5 : 1));
                work.add(next);
            }
            if (work.isEmpty() && !curves.isEmpty()) {
                double maxDistance = 0;
                for (final double distance : distanceMap.values()) {
                    if (distance > maxDistance) {
                        maxDistance = distance;
                    }
                }
                for (final Node curve : curves) {
                    final double relativeDistance = attributes.get(curve);
                    distanceMap.put(curve, maxDistance + relativeDistance);
                }
                while (!curves.isEmpty()) {
                    final Node curve = curves.remove(0);
                    for (final Node next : curve.nextNodes) {
                        if (distanceMap.containsKey(next)) {
                            continue;
                        }
                        if (!next.isCurve()) {
                            work.add(next);
                            continue;
                        }
                        curves.add(next);
                        distanceMap.put(next, attributes.get(next) + maxDistance);
                    }
                }
                maxDistance = 0;
                for (final double distance : distanceMap.values()) {
                    if (distance > maxDistance) {
                        maxDistance = distance;
                    }
                }
                center = null;
                if (work.isEmpty()) {
                    throw new RuntimeException("Curve exit must have size > 0");
                }
                for (final Node straight : work) {
                    boolean allCurves = true;
                    for (final Node prev : prevNodeMap.get(straight)) {
                        if (!prev.isCurve()) {
                            allCurves = false;
                            break;
                        }
                    }
                    if (allCurves) {
                        distanceMap.put(straight, maxDistance);
                        for (final Node otherStraight : work) {
                            if (straight.nextNodes.contains(otherStraight)) {
                                center = otherStraight;
                                break;
                            }
                        }
                        break;
                    }
                }
                work.clear();
                work.add(center);
                distanceMap.put(center, maxDistance + 0.5);
            }
        }
        return grid;
    }

    /**
     * Sets nodes for highlighting, may be useful when rendering valid targets.
     */
    public void highlightNodes(@Nullable Map<Integer, Integer> nodeToDamage) {
        this.highlightedNodeToDamage = nodeToDamage;
        repaint();
    }

    /**
     * Returns node identifier of the clicked node, or null if there are no nodes
     * at the given coordinates.
     */
    @Nullable
    public Integer getNodeId(int x, int y) {
        final Node target = Node.getNode(nodes, x, y, MapEditor.DIAMETER);
        return target == null ? null : target.id;
    }

    @Override
    public void paintComponent(Graphics g) {
	    if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
        drawPath(g2d);
        drawTargets(g2d);
        drawInfoBox(g2d);
        drawPlayers(g2d);
        // drawDistances(g2d); // For debugging
    }

    private void drawPath(Graphics2D g2d) {
        g2d.setColor(Color.WHITE);
        if (previous != null) {
            previous.drawPath(g2d);
        }
    }

    private void drawTargets(Graphics2D g2d) {
        if (highlightedNodeToDamage != null) {
            for (Map.Entry<Integer, Integer> entry : highlightedNodeToDamage.entrySet()) {
                final int nodeId = entry.getKey();
                if (nodeId < 0 || nodeId >= nodes.size()) continue;
                final Node node = nodes.get(nodeId);
                final int damage = entry.getValue();
                if (damage > 0) {
                    g2d.setFont(new Font("Arial", Font.PLAIN, 9));
                    g2d.setColor(Color.RED);
                    final int x = node.x - (damage >= 10 ? 5 : 2);
                    g2d.drawString(Integer.toString(damage), x, node.y + 3);
                }
                MapEditor.drawOval(g2d, node.x, node.y, 12, 12, true, false, Color.YELLOW, 1);
            }
        }
    }

    private void drawInfoBox(Graphics2D g2d) {
        int size = players.size() + stoppedPlayers.size();
        g2d.setColor(Color.GRAY);
        g2d.fillRect(getWidth() - 250, 0, 249, 5 + 15 * size);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(getWidth() - 250, 0, 249, 5 + 15 * size);
        int i = 0;
        for (LocalPlayer player : players) {
            player.draw(g2d, getWidth() - 235, i * 15 + 10, 0);
            player.drawStats(g2d, getWidth() - 220, i * 15 + 15);
            i++;
        }
        int pos = 0;
        for (LocalPlayer player : stoppedPlayers) {
            player.draw(g2d, getWidth() - 235, i * 15 + 10, 0);
            player.drawStats(g2d, getWidth() - 220, i * 15 + 15);
            if (player.lapsToGo < 0) {
                g2d.drawString(Integer.toString(pos++) + ".", getWidth() - 260, i * 15 + 15);
            } else {
                g2d.drawLine(getWidth() - 235, i * 15 + 10, getWidth() - 15, i * 15 + 10);
            }
            i++;
        }
    }

    private void drawPlayers(Graphics2D g2d) {
        for (final LocalPlayer player : players) {
            if (player == current) {
                player.drawStats(g2d, roll);
                player.highlight(g2d);
            }
            player.draw(g2d);
        }
    }

    private void drawDistances(Graphics2D g2d) {
        for (final Map.Entry<Node, Double> entry : distanceMap.entrySet()) {
            final int posX = entry.getKey().x - 5;
            final int posY = entry.getKey().y + 3;
            g2d.setFont(new Font("Arial", Font.PLAIN, 8));
            g2d.setColor(Color.BLUE);
            g2d.drawString(entry.getValue().toString(), posX, posY);
        }
    }

    private void addStoppedPlayer(LocalPlayer player) {
        stoppedPlayers.add(player);
        stoppedPlayers.sort((p1, p2) -> {
            if (p1.lapsToGo < 0 && p2.lapsToGo < 0) {
                final int index1 = stoppedPlayers.indexOf(p1);
                final int index2 = stoppedPlayers.indexOf(p2);
                return index1 > index2 ? 1 : -1;
            }
            int cmp = p1.compareTo(p2, distanceMap);
            if (cmp == 0) {
                final int index1 = stoppedPlayers.indexOf(p1);
                final int index2 = stoppedPlayers.indexOf(p2);
                return index1 > index2 ? 1 : -1;
            }
            return cmp;
        });
    }

    private void nextPlayer() {
	    // Drop stopped players
	    final Iterator<LocalPlayer> it = players.iterator();
	    while (it.hasNext()) {
	        final LocalPlayer player = it.next();
	        if (player.isStopped()) {
	            addStoppedPlayer(player);
	            it.remove();
            }
        }
        if (waitingPlayers.isEmpty()) {
            if (players.isEmpty()) {
                // This will make the Game thread stop
                stopped = true;
                return;
            }
            waitingPlayers.addAll(players);
            waitingPlayers.sort((p1, p2) -> p1.compareTo(p2, distanceMap));
        }
        previous = current;
        current = waitingPlayers.remove(0);
    }

    public static void main(final String[] args) {
        final JFrame f = new JFrame();
        final Game game = new Game(f);
        f.setContentPane(game);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.pack();
        f.setLocation(0, 0);
        new Thread(game).start();
        f.setVisible(true);
    }
}
