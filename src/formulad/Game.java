package formulad;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class Game extends JPanel {
    private final List<Node> nodes = new ArrayList<>();
    private BufferedImage backgroundImage;
    private Player current;
    private Integer roll;
    private static final Random r = new Random();
    private Map<Node, DamageAndPath> targets;
    private Map<Node, Integer> distanceMap;

	public Game() {
        backgroundImage = ImageCache.getImage("sebring.jpg");
        setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        final File file = new File("formulad.dat");
        final Collection<Node> loadedNodes = MapEditor.loadNodes(file);
        if (loadedNodes != null) {
            nodes.addAll(loadedNodes);
        }
        List<Node> grid = findGrid();
        current = new Player(1);
        current.move(new DamageAndPath(0, Collections.singletonList(grid.get(0))));
    }

	private List<Node> findGrid() {
        final List<Node> grid = new ArrayList<>();
        distanceMap = new HashMap<>();
        final List<Node> work = new ArrayList<>();
        for (final Node node : nodes) {
            if (node.type == MapEditor.FINISH) {
                work.add(node);
                distanceMap.put(node, 0);
            }
        }
        while (!work.isEmpty()) {
            final Node node = work.remove(0);
            final int distance = distanceMap.get(node);
            if (node.type == MapEditor.START) {
                grid.add(0, node);
            }
            for (final Node next : node.nextNodes) {
                if (!distanceMap.containsKey(next)) {
                    work.add(next);
                    distanceMap.put(next, distance + 1);
                    //System.err.println("distance " + (distance + 1));
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
        current.drawStats(g2d, roll);
        current.drawPath(g);
        current.draw(g2d);
        drawTargets(g2d);
    }

    private void drawTargets(final Graphics2D g2d) {
        if (targets != null) {
            for (Map.Entry<Node, DamageAndPath> entry : targets.entrySet()) {
                final int posX = entry.getKey().x;
                final int posY = entry.getKey().y;
                if (entry.getValue().getDamage() > 0) {
                    // TODO: Smaller font and centralize
                    g2d.setColor(Color.RED);
                    g2d.drawString(Integer.toString(entry.getValue().getDamage()), posX - 4, posY + 4);
                }
                MapEditor.drawOval(g2d, posX, posY, 12, 12, true, false, Color.YELLOW, 1);
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
            if (current.adjustRoll(roll, delta)) {
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
	        targets = current.findTargetNodes(roll);
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
                current.move(targets.get(target));
                roll = null;
                targets = null;
                repaint();
            }
        }
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
