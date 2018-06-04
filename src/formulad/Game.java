package formulad;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
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

public class Game extends JPanel {
    private final List<Node> nodes = new ArrayList<>();
    private final Map<Node, List<Node>> prevNodeMap = new HashMap<>();
    private Map<Node, Double> attributes = new HashMap<>();
    private BufferedImage backgroundImage;
    private Player previous;
    private Player current;
    private List<Player> waitingPlayers = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final List<Player> stoppedPlayers = new ArrayList<>();
    private Integer roll;
    public static final Random r = new Random();
    private Map<Node, DamageAndPath> targets;
    private final Map<Node, Double> distanceMap = new HashMap<>();

	public Game() {
        backgroundImage = ImageCache.getImage("sebring.jpg");
        setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        final File file = new File("formulad.dat");
        MapEditor.loadNodes(file, nodes, attributes);
        for (final Node node : nodes) {
            for (final Node next : node.nextNodes) {
                prevNodeMap.computeIfAbsent(next, _node -> new ArrayList<>()).add(node);
            }
        }
        List<Node> grid = findGrid().subList(0, 10);
        for (final Node node : grid) {
            players.add(new Player(node, attributes.get(node), 1));
        }
        waitingPlayers.addAll(players);
        current = waitingPlayers.remove(0);
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

    @Override
    public void paintComponent(Graphics g) {
	    if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
        current.drawStats(g2d, roll);
        if (previous != null) {
            previous.drawPath(g);
        }
        drawTargets(g2d);

        // Draw info box
        g.setColor(Color.GRAY);
        g.fillRect(getWidth() - 200, 0, 199, 5 + 15 * players.size());
        g.setColor(Color.BLACK);
        g.drawRect(getWidth() - 200, 0, 199, 5 + 15 * players.size());
        int i = 0;
        for (Player player : players) {
            player.draw(g2d, getWidth() - 185, i * 15 + 10, 0);
            player.drawStats(g2d, getWidth() - 170, i * 15 + 15);
            i++;
        }

        for (final Player player : players) {
            if (player == current) {
                player.highlight(g2d);
            }
            player.draw(g2d);
        }
        // Debugging distance map
        /*
        for (final Map.Entry<Node, Double> entry : distanceMap.entrySet()) {
            final int posX = entry.getKey().x - 5;
            final int posY = entry.getKey().y + 3;
            g2d.setFont(new Font("Arial", Font.PLAIN, 8));
            g2d.setColor(Color.BLUE);
            g2d.drawString(entry.getValue().toString(), posX, posY);
        }
        */
    }

    private void drawTargets(final Graphics2D g2d) {
        if (targets != null) {
            for (Map.Entry<Node, DamageAndPath> entry : targets.entrySet()) {
                final int posX = entry.getKey().x;
                final int posY = entry.getKey().y;
                final int damage = entry.getValue().getDamage();
                if (damage > 0) {
                    g2d.setFont(new Font("Arial", Font.PLAIN, 9));
                    g2d.setColor(Color.RED);
                    final int x = posX - (damage >= 10 ? 5 : 2);
                    g2d.drawString(Integer.toString(entry.getValue().getDamage()), x, posY + 3);
                }
                MapEditor.drawOval(g2d, posX, posY, 12, 12, true, false, Color.YELLOW, 1);
            }
        }
    }

    // TODO: Is the distribution equal?
    private static int roll(int gear) {
        switch (gear) {
        case 1: return r.nextInt(2) + 1; // d4
        case 2: return r.nextInt(3) + 2; // d6
        case 3: return r.nextInt(5) + 4; // d8
        case 4: return r.nextInt(6) + 7; // d12
        case 5: return r.nextInt(10) + 11; // d20
        case 6: return r.nextInt(10) + 21; // d20
        }
        throw new RuntimeException("Invalid gear: " + gear);
    }

    private static boolean rollDamage() {
	    return r.nextInt(20) < 4;
    }

    private void adjustRoll(final int delta) {
	    if (roll != null) {
            if (current.adjustRoll(roll, delta)) {
                targets = current.findTargetNodes(roll, false, players);
                repaint();
            }
        }
    }

    private void switchGear(final int newGear) {
	    if (roll != null) {
	        return;
        }
        if (current.switchGear(newGear)) {
	        roll = roll(newGear);
	        targets = current.findTargetNodes(roll, true, players);
	        if (targets == null) {
	            current.stop();
	            nextPlayer();
	            roll = null;
            }
            repaint();
        }
    }

    private void moveWithAI() {
	    current.sendPlayerData(players);
	    if (roll == null) {
	        int newGear = current.decideGear();
	        if (current.switchGear(newGear)) {
	            roll = roll(newGear);
            } else {
	            return; // invalid move
            }
        }
        int oldRoll = roll;
        Map<Node, DamageAndPath> targets = current.findTargetNodes(roll, true, players);
	    AI.NodeOrAdjustment a = current.decideTarget(targets);
        DamageAndPath damageAndPath = targets.get(a.node);
	    while (damageAndPath == null) {
	        if (a.adjust == 0) {
	            roll = oldRoll;
	            return; // death??
            }
            adjustRoll(a.adjust);
            targets = current.findTargetNodes(roll, true, players);
            a = current.decideTarget(targets);
            damageAndPath = targets.get(a.node);
        }
        current.move(damageAndPath);
        current.collide(players, prevNodeMap);
        if (roll == 20 || roll == 30) {
            // TODO: Fix adjustments
            Player.possiblyAddEngineDamage(players);
        }
        roll = null;
        nextPlayer();
        repaint();
    }

    private void selectNode(final int x, final int y) {
	    if (roll != null) {
            final Node target = Node.getNode(nodes, x, y, MapEditor.DIAMETER);
            if (target != null && targets.containsKey(target)) {
                current.move(targets.get(target));
                current.collide(players, prevNodeMap);
                if (roll == 20 || roll == 30) {
                    // TODO: Fix adjustments
                    Player.possiblyAddEngineDamage(players);
                }
                roll = null;
                targets = null;
                nextPlayer();
                repaint();
            }
        }
    }

    private List<Player> getStandings() {
        final List<Player> results = new ArrayList<>(stoppedPlayers);
        results.sort((p1, p2) -> {
            if (p1.lapsToGo == 0 && p2.lapsToGo == 0) {
                return stoppedPlayers.indexOf(p1) - stoppedPlayers.indexOf(p2);
            }
            int cmp = p1.compareTo(p2, distanceMap);
            if (cmp == 0) {
                return stoppedPlayers.indexOf(p1) - stoppedPlayers.indexOf(p2);
            }
            return cmp;
        });
        return results;
    }

    private void nextPlayer() {
	    // Drop stopped players
	    final Iterator<Player> it = players.iterator();
	    while (it.hasNext()) {
	        final Player player = it.next();
	        if (player.isStopped()) {
	            stoppedPlayers.add(player);
	            it.remove();
            }
        }
        if (waitingPlayers.isEmpty()) {
            if (players.isEmpty()) {
                throw new RuntimeException("DONE");
            }
            waitingPlayers.addAll(players);
            waitingPlayers.sort((p1, p2) -> p1.compareTo(p2, distanceMap));
        }
        previous = current;
        current = waitingPlayers.remove(0);
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
                case 'a':
                    p.moveWithAI();
                    break;
                case 'q':
                    f.setVisible(false);
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
