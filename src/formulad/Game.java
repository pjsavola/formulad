package formulad;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class Game extends JPanel {
    private final List<Node> nodes = new ArrayList<>();
    private BufferedImage backgroundImage;
    private Node car;
    private int hitpoints = 18;
    private int gear = 0;
    private Integer roll;
    private int adjust = 0;
    private static final Random r = new Random();
    private Map<Node, DamageAndPath> targets;
    private int curveStops = 0;
    private List<Node> route;
    private Double angle;

	public Game() {
        backgroundImage = ImageCache.getImage("sebring.jpg");
        setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        final File file = new File("formulad.dat");
        final Collection<Node> loadedNodes = MapEditor.loadNodes(file);
        if (loadedNodes != null) {
            nodes.addAll(loadedNodes);
        }
        List<Node> grid = findGrid();
        car = grid.get(0);
    }

    private static class DamageAndPath {
	    private int damage;
	    private List<Node> path;

	    public DamageAndPath(final int damage,
                             final List<Node> head) {
	        this.damage = damage;
	        path = head;
        }

        public DamageAndPath addPrefix(final int damage, final List<Node> prefix) {
	        this.damage += damage;
	        path.addAll(0, prefix);
	        return this;
        }
    }

    // Finds path from initial node to the given node at given distance
    private static List<Node> findPath(final Node node,
                                       final int distance,
                                       final Map<Integer, Set<Node>> distanceMap) {
	    final List<Node> path = new ArrayList<>();
	    path.add(node);
	    for (int i = distance - 1; i >= 0; i--) {
	        for (final Node previous : distanceMap.get(i)) {
	            if (previous.nextNodes.contains(path.get(0))) {
	                path.add(0, previous);
	                break;
                }
            }
        }
        if (path.size() != distance + 1) {
	        throw new RuntimeException("Unable to find path");
        }
        return path;
    }

    private static void addWork(final Node node,
                                final int currentDistance,
                                final Map<Integer, Set<Node>> distanceMap) {
        distanceMap.computeIfAbsent(currentDistance + 1, _node -> new HashSet<>()).add(node);
    }

    private static Map<Node, DamageAndPath> findNodes(final Node startNode,
                                                      final int targetDistance,
                                                      final Set<Node> forbiddenNodes,
                                                      final boolean allowCurveEntry,
                                                      final int stopsDone) {
        // Set of visited non-curve nodes, for finding the shortest path in straights
        final Set<Node> visited = new HashSet<>();
        // For each node which is at target distance, calculate the damage and path
        final Map<Node, DamageAndPath> result = new HashMap<>();
        // Collects all nodes at certain distances, used for finding paths
        final Map<Integer, Set<Node>> distanceMap = new HashMap<>();
        addWork(startNode, -1, distanceMap);
        if (!startNode.isCurve()) {
            visited.add(startNode);
        }
        for (int distance = 0; distance <= targetDistance && distanceMap.containsKey(distance); distance++) {
            for (final Node node : distanceMap.get(distance)) {
                if (distance == targetDistance) {
                    result.put(node, new DamageAndPath(0, findPath(node, distance, distanceMap)));
                    continue;
                }
                for (final Node next : node.nextNodes) {
                    if (forbiddenNodes.contains(next)) {
                        // node is blocked
                        continue;
                    }
                    if (node.isCurve() || next.isCurve()) {
                        if (!node.isCurve()) {
                            // entering curve
                            if (allowCurveEntry) {
                                addWork(next, distance, distanceMap);
                            }
                        } else if (!next.isCurve()) {
                            // exiting curve
                            final int stopsToDo = (node.type == MapEditor.CURVE_1 ? 1 : 2) - stopsDone;
                            if (stopsToDo <= 1) {
                                final boolean allowEntry = stopsToDo <= 0;
                                final int damage = stopsToDo <= 0 ? 0 : targetDistance - distance;
                                final List<Node> path = findPath(node, distance, distanceMap);
                                findNodes(next, targetDistance - distance - 1, forbiddenNodes, allowEntry, 0)
                                    .forEach((n, dp) -> result.merge(
                                        n,
                                        dp.addPrefix(damage, path),
                                        (dp1, dp2) -> dp1.damage <= dp2.damage ? dp1 : dp2
                                    ));

                            }
                        } else {
                            // curve
                            addWork(next, distance, distanceMap);
                        }
                    } else {
                        // straight
                        if (visited.add(next)) {
                            addWork(next, distance, distanceMap);
                        }
                    }
                }
            }
        }
        return result;
    }

	private List<Node> findGrid() {
        final List<Node> grid = new ArrayList<>();
        final Set<Node> visited = new HashSet<>();
        final List<Node> work = new ArrayList<>();
        for (final Node node : nodes) {
            if (node.type == MapEditor.FINISH) {
                work.add(node);
                visited.add(node);
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
        return grid;
    }

    @Override
    public void paintComponent(Graphics g) {
	    if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
	    if (roll != null) {
            g2d.setColor(Color.GREEN);
            final int width = (roll + adjust >= 10) ? 25 : 20;
            final int x = (roll + adjust >= 10) ? 43 : 46;
            MapEditor.drawOval(g2d, 50, 20, width, 20, true, true, Color.BLACK, 1);
            g.drawString(Integer.toString(roll + adjust), x, 24);
        }
        g2d.setColor(Color.RED);
        final int width = (hitpoints + adjust >= 10) ? 25 : 20;
        final int x = (hitpoints + adjust >= 10) ? 13 : 16;
        MapEditor.drawOval(g2d, 20, 50, width, 20, true, true, Color.BLACK, 1);
        g.drawString(Integer.toString(hitpoints + adjust), x, 54);
        MapEditor.drawOval(g2d, 20, 20, 20, 20, true, true, Color.BLACK, 1);
        g2d.setColor(Color.WHITE);
        g.drawString(Integer.toString(gear), 16, 24);
        if (targets != null) {
            for (Map.Entry<Node, DamageAndPath> entry : targets.entrySet()) {
                final int posX = entry.getKey().x;
                final int posY = entry.getKey().y;
                if (entry.getValue().damage > 0) {
                    // TODO: Smaller font and centralize
                    g2d.setColor(Color.RED);
                    g2d.drawString(Integer.toString(entry.getValue().damage), posX - 4, posY + 4);
                }
                MapEditor.drawOval(g2d, posX, posY, 12, 12, true, false, Color.YELLOW, 1);
            }
        }
        if (route != null && route.size() > 1) {
            for (int i = 0; i < route.size() - 1; i++) {
                final Node n1 = route.get(i);
                final Node n2 = route.get(i + 1);
                g.drawLine(n1.x, n1.y, n2.x, n2.y);
                angle = Math.atan2(n2.y - n1.y, n2.x - n1.x);
            }
        }
        AffineTransform at = new AffineTransform();
        at.translate(car.x, car.y);
        g2d.transform(at);
        if (angle != null) g2d.rotate(angle);
        g2d.drawImage(ImageCache.getImage("car.png"), -6, -3, null);
        g2d.translate(-car.x, -car.y);
    }

    private static int roll(int gear) {
        switch (gear) {
        case 1: return r.nextInt(2) + 1;
        case 2: return r.nextInt(3) + 2;
        case 3: return r.nextInt(5) + 4;
        case 4: return r.nextInt(6) + 7;
        case 5: return r.nextInt(10) + 11;
        case 6: return r.nextInt(10) + 21;
        }
        throw new RuntimeException("Invalid gear: " + gear);
    }

    private void adjustRoll(final int delta) {
	    if (roll != null) {
	        final int newAdjust = adjust + delta;
            if (newAdjust <= 0 && roll + newAdjust >= 0 && hitpoints + newAdjust > 0) {
                adjust = newAdjust;
                final Map<Node, DamageAndPath> result = findNodes(car, roll + adjust, new HashSet<>(), true, curveStops);
                targets = new HashMap<>();
                for (final Map.Entry<Node, DamageAndPath> entry : result.entrySet()) {
                    if (entry.getValue().damage < hitpoints + adjust) {
                        targets.put(entry.getKey(), entry.getValue());
                    }
                }
                repaint();
            }
        }
    }

    private void switchGear(final int newGear) {
	    if (roll != null) {
	        return;
        }
	    if (newGear > 0 && newGear < gear - 1 && hitpoints > gear - 1 - newGear) {
            // downwards more than 1
            hitpoints -= gear - 1 - newGear;
            gear = newGear;
        }
	    if (Math.abs(newGear - gear) <= 1) {
	        route = null;
	        gear = newGear;
	        roll = roll(gear);
	        final Map<Node, DamageAndPath> result = findNodes(car, roll + adjust, new HashSet<>(), true, curveStops);
	        targets = new HashMap<>();
            for (final Map.Entry<Node, DamageAndPath> entry : result.entrySet()) {
                if (entry.getValue().damage < hitpoints + adjust) {
                    targets.put(entry.getKey(), entry.getValue());
                }
            }
            repaint();
        }
    }

    private void forfeit() {
	    if (roll != null) {
	        if (targets.isEmpty()) {
	            // crash and burn
            }
        }
    }

    private void selectNode(final int x, final int y) {
	    if (roll != null) {
            final Node target = Node.getNode(nodes, x, y, MapEditor.DIAMETER);
            if (target != null && targets.containsKey(target)) {
                car = target;
                roll = null;
                hitpoints += adjust;
                hitpoints -= targets.get(target).damage;
                final List<Node> path = targets.get(target).path;
                if (hitpoints < 1) {
                    // crash and burn
                }
                adjust = 0;
                targets = null;
                boolean onlyCurves = true;
                for (final Node node : path) {
                    if (!node.isCurve()) {
                        onlyCurves = false;
                        break;
                    }
                }
                if (!onlyCurves) {
                    // path contained non-curve nodes
                    curveStops = 0;
                }
                if (target.isCurve()) {
                    // movement ended in a curve
                    curveStops++;
                }
                animate(path);
            }
        }
    }

    private void animate(final List<Node> path) {
	    route = path;
        repaint();
    }

    public static void main(final String[] args) {
        final JFrame f = new JFrame();
        final Game p = new Game();
        f.setContentPane(p);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.pack();
        f.setLocation(0, 0);
        f.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                switch (e.getKeyChar()) {
                case '1':
                    p.switchGear(1);
                    break;
                case '2':
                    p.switchGear(2);
                    break;
                case '3':
                    p.switchGear(3);
                    break;
                case '4':
                    p.switchGear(4);
                    break;
                case '5':
                    p.switchGear(5);
                    break;
                case '6':
                    p.switchGear(6);
                    break;
                case '+':
                    p.adjustRoll(1);
                    break;
                case '-':
                    p.adjustRoll(-1);
                    break;
                case 'q':
                    p.forfeit();
                    break;
                }
            }
            @Override
            public void keyPressed(KeyEvent e) {
            }
            @Override
            public void keyReleased(KeyEvent e) {
            }
        });
        p.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                p.selectNode(e.getX(), e.getY());
            }
            @Override
            public void mousePressed(MouseEvent e) {
            }
            @Override
            public void mouseReleased(MouseEvent e) {
            }
            @Override
            public void mouseEntered(MouseEvent e) {
            }
            @Override
            public void mouseExited(MouseEvent e) {
            }
        });
        f.setVisible(true);
    }
}
