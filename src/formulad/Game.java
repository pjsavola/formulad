package formulad;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
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
    private Map<Node, SearchResult> targets;
    private int curveStops = 0;
    private List<Node> animation;

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

    private static class SearchResult {
	    public final int damage;
	    public final List<Node> path;

	    public SearchResult(final int damage, final List<Node> path) {
	        this.damage = damage;
	        this.path = path;
        }

        public SearchResult(final int damage, final List<Node> path, final SearchResult other) {
            this.damage = damage + other.damage;
            this.path = other.path;
            this.path.addAll(0, path);
        }
    }

    private static Map<Node, SearchResult> findNodes(final Node targetNode,
                                                     final int targetDistance,
                                                     final Set<Node> forbiddenNodes,
                                                     final boolean allowCurveEntry,
                                                     final int stopsDone) {
        // Set of visited non-curve nodes, for finding the shortest path in straights
        final Set<Node> visited = new HashSet<>();
        final List<Node> work = new ArrayList<>();
        final Map<Node, SearchResult> result = new HashMap<>();
        final Map<Node, List<Node>> paths = new HashMap<>();
        work.add(targetNode);
        work.add(null);
        paths.computeIfAbsent(targetNode, _key -> new ArrayList<>()).add(targetNode);
        if (!targetNode.isCurve()) {
            visited.add(targetNode);
        }
        int distance = 0;
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            if (node == null) {
                distance++;
                if (!work.isEmpty()) work.add(null);
                continue;
            }
            if (distance == targetDistance) {
                result.put(node, new SearchResult(0, paths.get(node)));
            } else if (distance < targetDistance) {
                for (final Node next : node.nextNodes) {
                    if (forbiddenNodes.contains(next)) {
                        // node is blocked
                        continue;
                    }
                    if (node.isCurve() || next.isCurve()) {
                        if (!node.isCurve()) {
                            // entering curve
                            if (allowCurveEntry) {
                                work.add(next);
                                paths.computeIfAbsent(next, _key -> new ArrayList<>(paths.get(node))).add(next);
                            }
                        } else if (!next.isCurve()) {
                            // exiting curve
                            final int stopsToDo = (node.type == MapEditor.CURVE_1 ? 1 : 2) - stopsDone;
                            if (stopsToDo <= 0) {
                                findNodes(next, targetDistance - distance - 1, forbiddenNodes, true, 0)
                                    .forEach((n, sr) -> result.merge(
                                        n,
                                        new SearchResult(0, paths.get(node), sr),
                                        (sr1, sr2) -> sr1.damage <= sr2.damage ? sr1 : sr2
                                    ));
                            } else if (stopsToDo == 1) {
                                final int damage = targetDistance - distance;
                                findNodes(next, damage - 1, forbiddenNodes, false, 0)
                                    .forEach((n, sr) -> result.merge(
                                        n,
                                        new SearchResult(damage, paths.get(node), sr),
                                        (sr1, sr2) -> sr1.damage <= sr2.damage ? sr1 : sr2
                                    ));
                            }
                        } else {
                            // curve
                            work.add(next);
                            paths.computeIfAbsent(next, _key -> new ArrayList<>(paths.get(node))).add(next);
                        }
                    } else {
                        // straight
                        if (visited.add(next)) {
                            work.add(next);
                            paths.computeIfAbsent(next, _key -> new ArrayList<>(paths.get(node))).add(next);
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
        {
            final Rectangle r = new Rectangle(-5, -3, 10, 6);
            Path2D.Double path = new Path2D.Double();
            path.append(r, false);
            AffineTransform at = new AffineTransform();
            at.translate(car.x, car.y);
            //at.rotate(node.angle);
            path.transform(at);
            g2d.draw(path);
        }

        if (targets != null) {
            g2d.setColor(Color.YELLOW);
            for (Node node : targets.keySet()) {
                final Rectangle r = new Rectangle(-5, -3, 10, 6);
                Path2D.Double path = new Path2D.Double();
                path.append(r, false);
                AffineTransform at = new AffineTransform();
                at.translate(node.x, node.y);
                //at.rotate(no.angle);
                path.transform(at);
                g2d.draw(path);
            }
        }

        if (animation != null && animation.size() > 1) {
            for (int i = 0; i < animation.size() - 1; i++) {
                final Node n1 = animation.get(i);
                final Node n2 = animation.get(i + 1);
                g.drawLine(n1.x, n1.y, n2.x, n2.y);
            }
        }
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
                final Map<Node, SearchResult> result = findNodes(car, roll + adjust, new HashSet<>(), true, curveStops);
                targets = new HashMap<>();
                for (final Map.Entry<Node, SearchResult> entry : result.entrySet()) {
                    if (entry.getValue().damage < hitpoints + adjust) {
                        targets.put(entry.getKey(), entry.getValue());
                    }
                }
                repaint();
            }
        }
    }

    private void switchGear(final int newGear) {
	    if (newGear > 0 && newGear < gear - 1 && hitpoints > gear - 1 - newGear) {
            // downwards more than 1
            hitpoints -= gear - 1 - newGear;
            gear = newGear;
        }
	    if (roll == null && Math.abs(newGear - gear) <= 1) {
	        animation = null;
	        gear = newGear;
	        roll = roll(gear);
	        final Map<Node, SearchResult> result = findNodes(car, roll + adjust, new HashSet<>(), true, curveStops);
	        targets = new HashMap<>();
            for (final Map.Entry<Node, SearchResult> entry : result.entrySet()) {
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
	    animation = path;
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
