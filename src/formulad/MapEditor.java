package formulad;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class MapEditor extends JPanel {
    public static final int STRAIGHT = 1;
    public static final int CURVE_2 = 2;
    public static final int CURVE_1 = 3;
    public static final int START = 4;
    public static final int FINISH = 5;

    public static final int DIAMETER = 8;

    private static final Color ARC_BEGIN = new Color(0x333300);
    private static final Color ARC_END = new Color(0x990000);
    private static final Color LIGHT_RED = new Color(0xFF6600);

    private int nextNodeId;
    private final List<Node> nodes = new ArrayList<>();
    private final Map<Node, Double> attributes = new HashMap<>();
    private Node selectedNode;
    private BufferedImage backgroundImage;
    private int stroke = STRAIGHT;

    // After drawing an edge, automatically select the target node.
    private boolean autoSelectMode = true;

	public MapEditor() {
        addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    final Node node = Node.getNode(nodes, e.getX(), e.getY(), DIAMETER);
                    if (node == null) {
                        selectedNode = null;
                        nodes.add(new Node(nextNodeId++, e.getX(), e.getY(), stroke));
                    } else if (selectedNode == null) {
                        selectedNode = node;
                    } else if (selectedNode != node) {
                        selectedNode.addNext(node);
                        selectedNode = autoSelectMode ? node : (node.nextNodes.isEmpty() ? node : null);
                    } else {
                        selectedNode = null;
                    }
                    repaint();
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    final Node node = Node.getNode(nodes, e.getX(), e.getY(), DIAMETER / 2 + 1);
                    if (node != null) {
                        if (selectedNode == node) {
                            selectedNode = null;
                        }
                        nodes.remove(node);
                        for (final Node n : nodes) {
                            n.nextNodes.remove(node);
                        }
                        attributes.remove(node);
                        repaint();
                    }
                }
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
	}

	public void setStroke(final int stroke) {
	    this.stroke = stroke;
    }

    public void toggleSelectMode() {
	    autoSelectMode = !autoSelectMode;
    }

    @Override
    public void paintComponent(Graphics g) {
	    if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
        for (final Node node : nodes) {
            drawNode(g2d, node);
        }
        if (selectedNode != null) {
            drawOval(g2d, selectedNode.x, selectedNode.y, DIAMETER + 2, DIAMETER + 2, true, false, Color.WHITE, 1);
        }
        drawArcs(g2d, nodes);
    }

    private void open() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        final int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            backgroundImage = ImageCache.getImage(selectedFile.getAbsolutePath());
            setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        }
    }

    private void save() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        final int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            try (final PrintWriter writer = new PrintWriter(selectedFile, "UTF-8")) {
                final Map<Node, Integer> idMap = new HashMap<>();
                for (int i = 0; i < nodes.size(); i++) {
                    final Node node = nodes.get(i);
                    idMap.put(node, i);
                    writer.print(i);
                    writer.print(" ");
                    writer.print(node.x);
                    writer.print(" ");
                    writer.print(node.y);
                    writer.print(" ");
                    writer.println(node.type);
                }
                writer.println();
                for (final Node node : nodes) {
                    for (final Node nextNode : node.nextNodes) {
                        writer.print(idMap.get(node));
                        writer.print(" ");
                        writer.println(idMap.get(nextNode));
                    }
                }
                if (!attributes.isEmpty()) {
                    writer.println();
                    for (final Map.Entry<Node, Double> entry : attributes.entrySet()) {
                        writer.print(idMap.get(entry.getKey()));
                        writer.print(" ");
                        writer.println(entry.getValue());
                    }
                }

            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    private void load() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        final int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            loadNodes(selectedFile, nodes, attributes);
            nextNodeId += nodes.size();
            repaint();
        }
    }

    public static void loadNodes(final File selectedFile,
                                 final Collection<Node> nodes,
                                 final Map<Node, Double> attributes) {
        try (final BufferedReader br = new BufferedReader(new FileReader(selectedFile))) {
            // File format begins with nodes, then a single empty line and then edges.
            final Map<Integer, Node> idMap = new HashMap<>();
            final Map<Node, Double> attrMap = new HashMap<>();
            int emptyLines = 0;
            String line;
            while ((line = br.readLine()) != null) {
                final String[] parts = line.split(" ");
                if (parts.length == 1 && "".equals(parts[0])) {
                    emptyLines++;
                } else if (emptyLines == 1) {
                    final int fromId = Integer.parseInt(parts[0]);
                    final int toId = Integer.parseInt(parts[1]);
                    idMap.get(fromId).nextNodes.add(idMap.get(toId));
                } else if (emptyLines == 0) {
                    final int id = Integer.parseInt(parts[0]);
                    final int x = Integer.parseInt(parts[1]);
                    final int y = Integer.parseInt(parts[2]);
                    final int type = Integer.parseInt(parts[3]);
                    idMap.put(id, new Node(id, x, y, type));
                } else {
                    final int id = Integer.parseInt(parts[0]);
                    final double attribute = Double.parseDouble(parts[1]);
                    attrMap.put(idMap.get(id), attribute);
                }
            }
            // The file format was appraently good.
            nodes.clear();
            nodes.addAll(idMap.values());
            attributes.clear();
            attributes.putAll(attrMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setAttribute() {
	    if (selectedNode != null) {
            final Object attr = JOptionPane.showInputDialog(
                this,
                "Set attribute:",
                "Set attribute",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                attributes.get(selectedNode)
            );
            if (attr != null && !attr.toString().isEmpty()) {
                attributes.put(selectedNode, Double.valueOf(attr.toString()));
            } else {
                attributes.remove(selectedNode);
            }
            if (autoSelectMode) {
                selectedNode = null;
                repaint();
            }
        }
    }

    public static void drawNode(final Graphics2D g, final Node node) {
        final Color color;
        switch (node.type) {
            case STRAIGHT:
                color = Color.GREEN;
                break;
            case CURVE_2:
                color = Color.RED;
                break;
            case CURVE_1:
                color = LIGHT_RED;
                break;
            case START:
                color = Color.WHITE;
                break;
            case FINISH:
                color = Color.BLUE;
                break;
            default:
                throw new RuntimeException("Unknown node type");
        }
        drawOval(g, node.x, node.y, DIAMETER, DIAMETER, true, true, color, 0);
    }

    public static void drawArcs(final Graphics2D g, final Collection<Node> nodes) {
        final Color tmpC = g.getColor();
        for (final Node node : nodes) {
            for (final Node next : node.nextNodes) {
                final int midX = (node.x + next.x) / 2;
                final int midY = (node.y + next.y) / 2;
                g.setColor(ARC_BEGIN);
                g.drawLine(node.x, node.y, midX, midY);
                g.setColor(ARC_END);
                g.drawLine(midX, midY, next.x, next.y);
            }
        }
        g.setColor(tmpC);
    }

    public static void drawOval(final Graphics2D g,
                                final int x,
                                final int y,
                                final int width,
                                final int height,
                                final boolean centered,
                                final boolean filled,
                                final Color color,
                                final int lineThickness) {
        // Store before changing.
        final Stroke tmpS = g.getStroke();
        final Color tmpC = g.getColor();

        g.setColor(color);
        g.setStroke(new BasicStroke(lineThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        final int x2 = centered ? x - (width / 2) : x;
        final int y2 = centered ? y - (height / 2) : y;

        if (filled) g.fillOval(x2, y2, width, height);
        else g.drawOval(x2, y2, width, height);

        // Set values to previous when done.
        g.setColor(tmpC);
        g.setStroke(tmpS);
    }

    public static void main(final String[] args) {
        final JFrame f = new JFrame();
        final MapEditor p = new MapEditor();
        f.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                final char c = e.getKeyChar();
                switch (c) {
                case '1':
                    p.setStroke(STRAIGHT);
                    break;
                case '2':
                    p.setStroke(CURVE_2);
                    break;
                case '3':
                    p.setStroke(CURVE_1);
                    break;
                case '4':
                    p.setStroke(START);
                    break;
                case '5':
                    p.setStroke(FINISH);
                    break;
                case 'a':
                    p.toggleSelectMode();
                    break;
                case 'o':
                    p.open();
                    f.pack();
                    break;
                case 's':
                    p.save();
                    break;
                case 'l':
                    p.load();
                    break;
                case 'z':
                    p.setAttribute();
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
        f.setContentPane(p);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.pack();
        f.setLocation(0, 0);
        f.setVisible(true);
    }
}
