package formulad;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.annotation.Nullable;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import formulad.ai.Node;
import org.apache.commons.lang3.tuple.Pair;

public class MapEditor extends JPanel {

    public static final int DIAMETER = 8;

    private static final Color ARC_BEGIN = new Color(0x333300);
    private static final Color ARC_END = new Color(0x990000);
    private static final Color LIGHT_RED = new Color(0xFF6600);

    private int nextNodeId;
    private final List<Node> nodes = new ArrayList<>();
    private final Map<Node, Double> attributes = new HashMap<>();
    private final Map<Node, Point> coordinates = new HashMap<>();
    private Node selectedNode;
    private BufferedImage backgroundImage;
    private String backgroundImageFileName;
    private Node.Type stroke = Node.Type.STRAIGHT;

    // After drawing an edge, automatically select the target node.
    private boolean autoSelectMode = true;

    public enum Corner { NE, SE, SW, NW };

	public MapEditor(JFrame frame) {
        addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    final Node node = getNode(nodes, coordinates, e.getX(), e.getY(), DIAMETER);
                    if (node == null) {
                        selectedNode = null;
                        final Node newNode = new Node(nextNodeId++, stroke);
                        nodes.add(newNode);
                        coordinates.put(newNode, new Point(e.getX(), e.getY()));
                    } else if (selectedNode == null) {
                        selectedNode = node;
                    } else if (selectedNode != node) {
                        selectedNode.addChild(node);
                        selectedNode = autoSelectMode ? node : (node.childCount() == 0 ? node : null);
                    } else {
                        selectedNode = null;
                    }
                    repaint();
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    final Node node = getNode(nodes, coordinates, e.getX(), e.getY(), DIAMETER / 2 + 1);
                    if (node != null) {
                        if (selectedNode == node) {
                            selectedNode = null;
                        }
                        nodes.remove(node);
                        for (Node n : nodes) {
                            n.removeChild(node);
                        }
                        attributes.remove(node);
                        coordinates.remove(node);
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
        frame.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                final char c = e.getKeyChar();
                switch (c) {
                    case '1':
                        setStroke(Node.Type.STRAIGHT);
                        break;
                    case '2':
                        setStroke(Node.Type.CURVE_2);
                        break;
                    case '3':
                        setStroke(Node.Type.CURVE_1);
                        break;
                    case '4':
                        setStroke(Node.Type.START);
                        break;
                    case '5':
                        setStroke(Node.Type.FINISH);
                        break;
                    case 'a':
                        toggleSelectMode();
                        break;
                    case 'o':
                        open();
                        frame.pack();
                        break;
                    case 's':
                        save();
                        break;
                    case 'l':
                        load();
                        break;
                    case 'z':
                        setAttribute();
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
	}

	private void setStroke(Node.Type stroke) {
	    this.stroke = stroke;
    }

    private void toggleSelectMode() {
	    autoSelectMode = !autoSelectMode;
    }

    @Override
    public void paintComponent(Graphics g) {
	    if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
        for (Node node : nodes) {
            drawNode(g2d, node);
        }
        if (selectedNode != null) {
            final Point p = coordinates.get(selectedNode);
            drawOval(g2d, p.x, p.y, DIAMETER + 2, DIAMETER + 2, true, false, Color.WHITE, 1);
        }
        drawArcs(g2d, nodes);
    }

    boolean open() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        final int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            backgroundImage = ImageCache.getImageFromPath(selectedFile.getAbsolutePath());
            backgroundImageFileName = selectedFile.getName();
            setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
            return true;
        }
        return false;
    }

    private void save() {
	    if (backgroundImageFileName == null) {
	        return;
        }
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        final int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            try (final PrintWriter writer = new PrintWriter(selectedFile, "UTF-8")) {
                writer.print(backgroundImageFileName);
                writer.print(" ");
                writer.println(Corner.NE.ordinal());
                final Map<Node, Integer> idMap = new HashMap<>();
                for (int i = 0; i < nodes.size(); i++) {
                    final Node node = nodes.get(i);
                    final Point p = coordinates.get(node);
                    idMap.put(node, i);
                    writer.print(i);
                    writer.print(" ");
                    writer.print(p.x);
                    writer.print(" ");
                    writer.print(p.y);
                    writer.print(" ");
                    writer.println(node.getType().ordinal());
                }
                writer.println();
                for (Node node : nodes) {
                    node.forEachChild(child -> {
                        writer.print(idMap.get(node));
                        writer.print(" ");
                        writer.println(idMap.get(child));
                    });
                }
                if (!attributes.isEmpty()) {
                    writer.println();
                    for (Map.Entry<Node, Double> entry : attributes.entrySet()) {
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
            try (FileInputStream fis = new FileInputStream(selectedFile)) {
                loadNodes(fis, nodes, attributes, coordinates);
                nextNodeId += nodes.size();
                repaint();
            } catch (IOException e) {
                throw new RuntimeException("Can't bother to fix this properly", e);
            }
        }
    }

    public static Pair<String, Corner> loadHeader(String trackId) {
        try (InputStream is = FormulaD.class.getResourceAsStream("/" + trackId); InputStreamReader ir = new InputStreamReader(is); final BufferedReader br = new BufferedReader(ir)) {
            final String headerLine = br.readLine();
            if (headerLine != null && !headerLine.isEmpty()) {
                final String[] parts = headerLine.split(" ");
                return Pair.of(parts[0], Corner.values()[Integer.parseInt(parts[1])]);
            }
        } catch (IOException e) {
            FormulaD.log.log(Level.SEVERE, "Reading header of " + trackId + " failed", e);
        }
        return null;
    }

    public static Pair<String, Corner> loadNodes(InputStream map, Collection<Node> nodes, Map<Node, Double> attributes, Map<Node, Point> coordinates) {
	    Pair<String, Corner> result = null;
        try (InputStreamReader ir = new InputStreamReader(map); final BufferedReader br = new BufferedReader(ir)) {
            final String headerLine = br.readLine();
            if (headerLine != null && !headerLine.isEmpty()) {
                final String[] parts = headerLine.split(" ");
                result = Pair.of(parts[0], Corner.values()[Integer.parseInt(parts[1])]);
            }
            // File format begins with nodes, then a single empty line and then edges,
            // then a single empty line and then attributes.
            final Map<Integer, Node> idMap = new HashMap<>();
            final Map<Node, Double> attrMap = new HashMap<>();
            final Map<Node, Point> coordMap = new HashMap<>();
            int emptyLines = 0;
            String line;
            while ((line = br.readLine()) != null) {
                final String[] parts = line.split(" ");
                if (parts.length == 1 && "".equals(parts[0])) {
                    emptyLines++;
                } else if (emptyLines == 1) {
                    final int fromId = Integer.parseInt(parts[0]);
                    final int toId = Integer.parseInt(parts[1]);
                    idMap.get(fromId).addChild(idMap.get(toId));
                } else if (emptyLines == 0) {
                    final int id = Integer.parseInt(parts[0]);
                    final int x = Integer.parseInt(parts[1]);
                    final int y = Integer.parseInt(parts[2]);
                    final Node.Type type = Node.Type.values()[Integer.parseInt(parts[3])];
                    final Node node = new Node(id, type);
                    idMap.put(id, node);
                    coordMap.put(node, new Point(x, y));
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
            coordinates.clear();
            coordinates.putAll(coordMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void setAttribute() {
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

    private void drawNode(Graphics2D g2d, Node node) {
        final Color color;
        switch (node.getType()) {
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
        final Point p = coordinates.get(node);
        drawOval(g2d, p.x, p.y, DIAMETER, DIAMETER, true, true, color, 0);
    }

    private void drawArcs(Graphics2D g2d, Collection<Node> nodes) {
        final Color tmpC = g2d.getColor();
        for (Node node : nodes) {
            final Point p = coordinates.get(node);
            node.forEachChild(child -> {
                final Point np = coordinates.get(child);
                final int midX = (p.x + np.x) / 2;
                final int midY = (p.y + np.y) / 2;
                g2d.setColor(ARC_BEGIN);
                g2d.drawLine(p.x, p.y, midX, midY);
                g2d.setColor(ARC_END);
                g2d.drawLine(midX, midY, np.x, np.y);
            });
        }
        g2d.setColor(tmpC);
    }

    public static void drawOval(Graphics2D g2d, int x, int y, int width, int height, boolean centered, boolean filled, Color color, int lineThickness) {
        // Store before changing.
        final Stroke tmpS = g2d.getStroke();
        final Color tmpC = g2d.getColor();

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(lineThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        final int x2 = centered ? x - (width / 2) : x;
        final int y2 = centered ? y - (height / 2) : y;

        if (filled) g2d.fillOval(x2, y2, width, height);
        else g2d.drawOval(x2, y2, width, height);

        // Set values to previous when done.
        g2d.setColor(tmpC);
        g2d.setStroke(tmpS);
    }

    public static void main(String[] args) {
        final JFrame f = new JFrame();
        final MapEditor p = new MapEditor(f);
        f.setContentPane(p);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.pack();
        f.setLocation(0, 0);
        f.setVisible(true);
    }

    @Nullable
    public static Node getNode(Collection<Node> nodes, Map<Node, Point> coordinates, int x, int y, int threshold) {
        for (Node node : nodes) {
            final Point point = coordinates.get(node);
            if (Math.hypot(x - point.x, y - point.y) < threshold) {
                return node;
            }
        }
        return null;
    }
}
