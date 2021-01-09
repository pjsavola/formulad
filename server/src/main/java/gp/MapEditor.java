package gp;

import gp.ai.AIUtil;
import gp.ai.Node;
import gp.ai.NodeType;
import gp.ai.TrackData;
import gp.editor.*;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class MapEditor extends JPanel {

    static final int DIAMETER = 8;

    private static final Color ARC_BEGIN = new Color(0x333300);
    private static final Color ARC_END = new Color(0x990000);
    private static final Color LIGHT_RED = new Color(0xFF6600);

    private final JFrame frame;
    private int nextNodeId;
    private final List<Node> nodes = new ArrayList<>();
    private final Map<Node, Double> attributes = new HashMap<>();
    private Node selectedNode;
    private BufferedImage backgroundImage;
    private String backgroundImageFileName;
    private NodeType stroke = NodeType.STRAIGHT;
    private Corner infoBoxCorner = Corner.NE;

    // After drawing an edge, automatically select the target node.
    private boolean autoSelectMode = true;

    private final HashMap<NodeType, MenuItem> strokes = new HashMap<>();
    private final MenuItem setGarage;
    private final MenuItem setFinish;
    private final MenuItem setCurveDistance;
    private final MenuItem setGridAngle;

    private TrackLanes.Lane[] lanes = new TrackLanes.Lane[3];
    private final List<Node> debugNeighbors = new ArrayList<>();
    private final UndoStack stack = new UndoStack(nodes, attributes);
    private Double previousCurveDistance;
    private boolean showDistances;
    private boolean showIdentifiers;
    private boolean showLanes;
    private Dimension panelDim;
    private double scale = 1.0;

    public enum Corner { NE, SE, SW, NW }

	MapEditor(JFrame frame) {
	    this.frame = frame;
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final int x = (int) (e.getX() / scale);
                final int y = (int) (e.getY() / scale);
                if (e.getButton() == MouseEvent.BUTTON1) {
                    final Node node = getNode(nodes, x, y, DIAMETER);
                    if (node == null) {
                        if ((e.getModifiers() & InputEvent.SHIFT_MASK) != 0 && autoSelectMode && selectedNode != null) {
                            final String value = (String) JOptionPane.showInputDialog(
                                    MapEditor.this,
                                    "Number of new nodes:",
                                    "New node count",
                                    JOptionPane.PLAIN_MESSAGE,
                                    null,
                                    null,
                                    1
                            );
                            try {
                                final int nodeCount = Integer.parseInt(value);
                                if (nodeCount < 1) {
                                    return;
                                }
                                final Point start = selectedNode.getLocation();
                                final int dx = x - start.x;
                                final int dy = y - start.y;
                                if (dx * dx + dy * dy < 16 * DIAMETER * DIAMETER * nodeCount) {
                                    // Not enough space
                                    return;
                                }
                                final CreateNodesItem item = new CreateNodesItem(nextNodeId, stroke, start, selectedNode, dx, dy, nodeCount);
                                stack.execute(item);
                                nextNodeId += nodeCount;
                                select(item.getLastItem());
                                repaint();
                            } catch (NumberFormatException ex) {
                                return;
                            }
                            return;
                        }
                        final CreateNodeItem item = new CreateNodeItem(nextNodeId++, stroke, new Point(x, y), selectedNode);
                        stack.execute(item);
                        if (autoSelectMode && selectedNode != null) {
                            select(nodes.get(nodes.size() - 1));
                        } else {
                            select(null);
                        }
                    } else if (selectedNode == null) {
                        select(node);
                    } else if (selectedNode != node) {
                        if (autoSelectMode) {
                            final CreateEdgeItem item = new CreateEdgeItem(selectedNode, node);
                            stack.execute(item);
                        }
                        select(node);
                    } else {
                        select(null);
                    }
                    repaint();
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    final Node node = getNode(nodes, x, y, DIAMETER / 2 + 1);
                    if (node != null) {
                        if (selectedNode == node) {
                            select(null);
                        }
                        final RemoveNodeItem item = new RemoveNodeItem(node);
                        stack.execute(item);
                        repaint();
                    }
                }
            }
        });
        // TODO: Automatic distances
        final MenuBar menuBar = new MenuBar();
        final Menu fileMenu = new Menu("File");
        final Menu editMenu = new Menu("Edit");
        final Menu strokeMenu = new Menu("Stroke");
        final Menu view = new Menu("View");
        final Menu toolMenu = new Menu("Tools");
        final Menu validationMenu = new Menu("Validation");

        menuBar.add(fileMenu);
        final MenuItem loadBackgroundImage = new MenuItem("Load Background Image");
        loadBackgroundImage.addActionListener(e -> open());
        loadBackgroundImage.setShortcut(new MenuShortcut(KeyEvent.VK_O));
        fileMenu.add(loadBackgroundImage);
        final MenuItem loadTrackData = new MenuItem("Load Track Data");
        loadTrackData.addActionListener(e -> load());
        loadTrackData.setShortcut(new MenuShortcut(KeyEvent.VK_L));
        fileMenu.add(loadTrackData);
        final MenuItem saveTrackData = new MenuItem("Save Track Data as ...");
        saveTrackData.addActionListener(e -> save());
        saveTrackData.setShortcut(new MenuShortcut(KeyEvent.VK_S));
        fileMenu.add(saveTrackData);

        menuBar.add(editMenu);
        final MenuItem undo = new MenuItem("Undo");
        undo.addActionListener(e -> {
            stack.undo();
            select(null);
            repaint();
        });
        undo.setShortcut(new MenuShortcut(KeyEvent.VK_Z));
        editMenu.add(undo);
        stack.setMenuItem(undo);
        final MenuItem autoSelectMode = new MenuItem("Switch Mode");
        autoSelectMode.addActionListener(e -> toggleSelectMode());
        autoSelectMode.setShortcut(new MenuShortcut(KeyEvent.VK_A));
        editMenu.add(autoSelectMode);
        setGarage = new MenuItem("Set Garage");
        setGarage.addActionListener(e -> setAttribute());
        setGarage.setShortcut(new MenuShortcut(KeyEvent.VK_E));
        editMenu.add(setGarage);
        setFinish = new MenuItem("Set Finish");
        setFinish.addActionListener(e -> setFinish());
        setFinish.setShortcut(new MenuShortcut(KeyEvent.VK_F));
        editMenu.add(setFinish);
        setCurveDistance = new MenuItem("Set Curve Distance");
        setCurveDistance.addActionListener(e -> setAttribute());
        setCurveDistance.setShortcut(new MenuShortcut(KeyEvent.VK_E));
        editMenu.add(setCurveDistance);
        setGridAngle = new MenuItem("Set Grid Angle");
        setGridAngle.addActionListener(e -> setGridAngle());
        setGridAngle.setShortcut(new MenuShortcut(KeyEvent.VK_G));
        editMenu.add(setGridAngle);
        final MenuItem moveInfoBox = new MenuItem("Move Info Box");
        moveInfoBox.addActionListener(e -> switchInfoBoxCorner());
        moveInfoBox.setShortcut(new MenuShortcut(KeyEvent.VK_I));
        editMenu.add(moveInfoBox);

        menuBar.add(strokeMenu);
        final MenuItem strokeStraight = new MenuItem("Straight");
        strokeStraight.addActionListener(e -> setStroke(NodeType.STRAIGHT));
        strokeStraight.setShortcut(new MenuShortcut(KeyEvent.VK_T));
        strokeMenu.add(strokeStraight);
        strokes.put(NodeType.STRAIGHT, strokeStraight);
        final MenuItem strokeCurve1 = new MenuItem("1 Stop Curve");
        strokeCurve1.addActionListener(e -> setStroke(NodeType.CURVE_1));
        strokeCurve1.setShortcut(new MenuShortcut(KeyEvent.VK_1));
        strokeMenu.add(strokeCurve1);
        strokes.put(NodeType.CURVE_1, strokeCurve1);
        final MenuItem strokeCurve2 = new MenuItem("2 Stop Curve");
        strokeCurve2.addActionListener(e -> setStroke(NodeType.CURVE_2));
        strokeCurve2.setShortcut(new MenuShortcut(KeyEvent.VK_2));
        strokeMenu.add(strokeCurve2);
        strokes.put(NodeType.CURVE_2, strokeCurve2);
        final MenuItem strokeCurve3 = new MenuItem("3 Stop Curve");
        strokeCurve3.addActionListener(e -> setStroke(NodeType.CURVE_3));
        strokeCurve3.setShortcut(new MenuShortcut(KeyEvent.VK_3));
        strokeMenu.add(strokeCurve3);
        strokes.put(NodeType.CURVE_3, strokeCurve3);
        final MenuItem strokeFinishLine = new MenuItem("Finish Line");
        strokeFinishLine.addActionListener(e -> setStroke(NodeType.FINISH));
        strokeMenu.add(strokeFinishLine);
        strokes.put(NodeType.FINISH, strokeFinishLine);
        final MenuItem strokePitLane = new MenuItem("Pit Lane");
        strokePitLane.addActionListener(e -> setStroke(NodeType.PIT));
        strokePitLane.setShortcut(new MenuShortcut(KeyEvent.VK_P));
        strokeMenu.add(strokePitLane);
        strokes.put(NodeType.PIT, strokePitLane);
        final MenuItem strokeBlocked = new MenuItem("Blocked");
        strokeBlocked.addActionListener(e -> setStroke(NodeType.BLOCKED));
        strokeMenu.add(strokeBlocked);
        strokes.put(NodeType.BLOCKED, strokeBlocked);

        setStroke(NodeType.STRAIGHT);
        select(null);

        menuBar.add(view);
        final MenuItem zoomIn = new MenuItem("Zoom In");
        final MenuItem zoomOut = new MenuItem("Zoom Out");
        view.add(zoomIn);
        view.add(zoomOut);
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        zoomIn.addActionListener(e -> {
            final double oldScale = scale;
            scale = Math.min(4.0, scale * 1.1);
            if (scale != oldScale) {
                setPreferredSize(new Dimension((int) (backgroundImage.getWidth() * scale), (int) (backgroundImage.getHeight() * scale)));
                frame.pack();
                frame.setSize(Math.min(screenSize.width, frame.getWidth()), Math.min(screenSize.height - 100, frame.getHeight()));
            }
        });
        zoomIn.setShortcut(new MenuShortcut(KeyEvent.VK_PLUS));
        zoomOut.addActionListener(e -> {
            final double oldScale = scale;
            scale = Math.max(1.0, scale / 1.1);
            if (scale != oldScale) {
                setPreferredSize(new Dimension((int) (backgroundImage.getWidth() * scale), (int) (backgroundImage.getHeight() * scale)));
                frame.pack();
                frame.setSize(Math.min(screenSize.width, frame.getWidth()), Math.min(screenSize.height - 100, frame.getHeight()));
            }
        });
        zoomOut.setShortcut(new MenuShortcut(KeyEvent.VK_MINUS));

        menuBar.add(toolMenu);
        final MenuItem ids = new MenuItem("Show node identifiers");
        ids.addActionListener(e -> showIdentifiers());
        toolMenu.add(ids);
        final MenuItem unify = new MenuItem("Canonize node identifiers");
        unify.addActionListener(e -> unifyNodeIdentifiers());
        toolMenu.add(unify);
        final MenuItem moveAll = new MenuItem("Move all nodes");
        moveAll.addActionListener(e -> moveAll());
        toolMenu.add(moveAll);
        final MenuItem changeScale = new MenuItem("Scale node coordinates");
        changeScale.addActionListener(e -> scale());
        toolMenu.add(changeScale);
        final MenuItem deduceDistances = new MenuItem("Deduce Curve Distances");
        deduceDistances.addActionListener(e -> deduceDistances());
        toolMenu.add(deduceDistances);
        deduceDistances.setEnabled(false);
        final MenuItem autoFillEdges = new MenuItem("Auto-fill edges in straigths");
        toolMenu.add(autoFillEdges);
        autoFillEdges.addActionListener(e -> autoFillEdges());

        menuBar.add(validationMenu);
        final MenuItem showDistances = new MenuItem("Show Distances");
        showDistances.addActionListener(e -> showDistances());
        validationMenu.add(showDistances);

        final MenuItem showLanes = new MenuItem("Show Lanes");
        showLanes.addActionListener(e -> showLanes());
        validationMenu.add(showLanes);

        final MenuItem validate = new MenuItem("Validate Track");
        validate.addActionListener(e -> validateTrack());
        validate.setShortcut(new MenuShortcut(KeyEvent.VK_V));
        validationMenu.add(validate);

        frame.setMenuBar(menuBar);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (selectedNode != null) {
                    Point delta;
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_UP:
                            delta = new Point(0, -1);
                            break;
                        case KeyEvent.VK_DOWN:
                            delta = new Point(0, 1);
                            break;
                        case KeyEvent.VK_LEFT:
                            delta = new Point(-1, 0);
                            break;
                        case KeyEvent.VK_RIGHT:
                            delta = new Point(1, 0);
                            break;
                        case KeyEvent.VK_D:
                            debugNeighbors.clear();
                            TrackData.build(nodes, attributes);
                            final Map<Node, Set<Node>> collisionMap = TrackLanes.buildCollisionMap(nodes);
                            debugNeighbors.addAll(collisionMap.get(selectedNode));
                            repaint();
                            return;
                        default:
                            return;
                    }
                    final Point p = selectedNode.getLocation();
                    p.x += delta.x;
                    p.y += delta.y;
                    repaint();
                } else if (e.getKeyCode() == KeyEvent.VK_D) {
                    debugNeighbors.clear();
                    repaint();
                }
            }
        });
    }

    private void select(Node node) {
	    selectedNode = node;
	    if (selectedNode != null) System.out.println(selectedNode.getId());
	    setGarage.setEnabled(selectedNode != null && selectedNode.isPit());
	    setFinish.setEnabled(selectedNode != null && !selectedNode.isPit());
        setCurveDistance.setEnabled(selectedNode != null && selectedNode.isCurve());
	    setGridAngle.setEnabled(selectedNode != null && !selectedNode.isPit());
    }

	private void setStroke(NodeType stroke) {
	    this.stroke = stroke;
	    strokes.forEach((type, item) -> item.setEnabled(stroke != type));
    }

    private void toggleSelectMode() {
	    autoSelectMode = !autoSelectMode;
	    repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
	    super.paintComponent(g);
        AffineTransform at = new AffineTransform();
        at.scale(scale, scale);
        ((Graphics2D) g).transform(at);
	    if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
        if (showDistances || showIdentifiers) {
            for (Node node : nodes) {
                final String str;
                if (showIdentifiers) {
                    str = Integer.toString(node.getId());
                } else {
                    final double dist = node.getDistance();
                    if (dist < 0.0) continue;
                    str = Main.getDistanceString(dist);
                }
                final Point p = node.getLocation();
                final int posX = p.x - 5;
                final int posY = p.y + 3;
                g2d.setFont(new Font("Arial", Font.PLAIN, 8));
                g2d.setColor(Color.BLUE);
                g2d.drawString(str, posX, posY);
            }
            return;
        } else if (showLanes) {
            final Color tmpC = g2d.getColor();
            final Stroke tmpS = g2d.getStroke();
            g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (TrackLanes.Lane lane : lanes) {
                for (int i = 0; i < lane.getNodes().size(); ++i) {
                    final Node n1 = lane.getNodes().get(i);
                    final Node n2 = lane.getNodes().get((i + 1) % lane.getNodes().size());
                    if (!n1.hasChild(n2)) continue;

                    final Point p = n1.getLocation();
                    final Point np = n2.getLocation();
                    final int midX = (p.x + np.x) / 2;
                    final int midY = (p.y + np.y) / 2;
                    g2d.setColor(Color.GREEN);
                    g2d.drawLine(p.x, p.y, midX, midY);
                    g2d.setColor(Color.WHITE);
                    g2d.drawLine(midX, midY, np.x, np.y);
                }
            }
            g2d.setStroke(tmpS);
            g2d.setColor(tmpC);
            return;
        }
        for (Node node : nodes) {
            drawNode(g2d, node);
        }
        UIUtil.drawInfoBox(g2d, panelDim, 10, infoBoxCorner);
        final int x = UIUtil.getX(infoBoxCorner, panelDim, UIUtil.infoBoxWidth);
        final int y = UIUtil.getY(infoBoxCorner, panelDim, 5 + 15 * 10);
        if (selectedNode != null) {
            final Point p = selectedNode.getLocation();
            drawOval(g2d, p.x, p.y, DIAMETER + 2, DIAMETER + 2, false, Color.YELLOW, 1);
            final Double attr = attributes.get(selectedNode);
            if (attr != null) {
                drawOval(g2d, x + UIUtil.infoBoxWidth - 40, y + 40, 50, 50, true, Color.BLACK, 1);
                final String attrStr = Double.toString(attr);
                g2d.setColor(Color.WHITE);
                g2d.setFont(Player.rollFont);
                final int width = g2d.getFontMetrics().stringWidth(attrStr);
                g2d.drawString(attrStr, x + UIUtil.infoBoxWidth - 40 - width / 2, y + 48);
            }
        }
        g.setColor(Color.BLACK);
        g.setFont(Game.headerFont);
        final String modeStr = autoSelectMode ? "Mode: Draw edges" : "Mode: Select nodes";
        g.drawString(modeStr, x + 20, y + 15);
        drawArcs(g2d, nodes);
        for (Node neighbor : debugNeighbors) {
            final Point p = neighbor.getLocation();
            drawOval(g2d, p.x, p.y, DIAMETER, DIAMETER, true, Color.BLUE, 1);
        }

    }

    boolean open() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select background image");
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        fileChooser.addChoosableFileFilter(new Filter(Filter.imageExtensions, "Image only"));
        fileChooser.setAcceptAllFileFilterUsed(true);
        final int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            backgroundImage = ImageCache.getImageFromPath(selectedFile.getAbsolutePath());
            if (backgroundImage == null) {
                JOptionPane.showConfirmDialog(this, "Unable to open image " + selectedFile.getName(), "Error", JOptionPane.DEFAULT_OPTION);
                return false;
            }
            backgroundImageFileName = selectedFile.getName();
            panelDim = new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight());
            setPreferredSize(panelDim);
            frame.pack();
            final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(Math.min(screenSize.width, frame.getWidth()), Math.min(screenSize.height - 100, frame.getHeight()));
            return true;
        }
        return false;
    }

    private void save() {
	    if (backgroundImageFileName == null) {
	        return;
        }
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        fileChooser.addChoosableFileFilter(new Filter(Filter.dataExtensions, "Data file"));
        fileChooser.setAcceptAllFileFilterUsed(true);
        final int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            if (!selectedFile.getName().toLowerCase().endsWith(".dat")) {
                JOptionPane.showConfirmDialog(this, "Track data file must end with .dat. Track data is NOT saved!", "Wrong File Extension", JOptionPane.DEFAULT_OPTION);
                return;
            }
            try (final PrintWriter writer = new PrintWriter(selectedFile, "UTF-8")) {
                writer.print(backgroundImageFileName);
                writer.print(" ");
                writer.println(infoBoxCorner.ordinal());
                final Map<Node, Integer> idMap = new HashMap<>();
                for (int i = 0; i < nodes.size(); i++) {
                    final Node node = nodes.get(i);
                    final Point p = node.getLocation();
                    idMap.put(node, i);
                    writer.print(i);
                    writer.print(" ");
                    writer.print(p.x);
                    writer.print(" ");
                    writer.print(p.y);
                    writer.print(" ");
                    writer.print(node.getType().ordinal());
                    if (node.hasFinish()) {
                        writer.print(" F");
                    }
                    writer.println();
                }
                writer.println();
                for (Node node : nodes) {
                    node.forEachChild(child -> {
                        writer.print(idMap.get(node));
                        writer.print(" ");
                        writer.println(idMap.get(child));
                    });
                }
                writer.println();
                nodes.stream().filter(attributes::containsKey).forEach(node -> {
                    writer.print(idMap.get(node));
                    writer.print(" ");
                    writer.println(attributes.get(node));
                });
                nodes.stream().filter(Node::hasGarage).forEach(node -> {
                    writer.print(idMap.get(node));
                    writer.print(" ");
                    writer.println(1.0);
                });
                if (nodes.stream().anyMatch(node -> !Double.isNaN(node.getGridAngle()))) {
                    writer.println();
                    nodes.stream().filter(node -> !Double.isNaN(node.getGridAngle())).forEach(node -> {
                        writer.print(idMap.get(node));
                        writer.print(" ");
                        writer.println(node.getGridAngle());
                    });
                }
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            JOptionPane.showConfirmDialog(this, "Track data saved successfully", "Success", JOptionPane.DEFAULT_OPTION);
        }
    }

    private void load() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load track data");
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        fileChooser.addChoosableFileFilter(new Filter(Filter.dataExtensions, "Data file"));
        fileChooser.setAcceptAllFileFilterUsed(true);
        final int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            try (FileInputStream fis = new FileInputStream(selectedFile)) {
                final Pair<String, Corner> p = loadNodes(fis, nodes, attributes);
                if (p == null) {
                    JOptionPane.showConfirmDialog(this, "Wrong file format: " + selectedFile.getName(), "File Format Error", JOptionPane.DEFAULT_OPTION);
                    return;
                }
                select(null);
                stack.clear();
                showDistances = false;
                showIdentifiers = false;
                showLanes = false;
                lanes = new TrackLanes.Lane[3];
                previousCurveDistance = null;
                infoBoxCorner = p.getRight();
                nextNodeId += nodes.size();
                repaint();
            } catch (IOException e) {
                throw new RuntimeException("Can't bother to fix this properly", e);
            }
        }
    }

    private static Pair<String, Corner> parseHeaderRow(String headerLine) {
        if (headerLine != null && !headerLine.isEmpty()) {
            final int separatorIndex = headerLine.indexOf(".");
            final String[] parts = headerLine.substring(separatorIndex).split(" ");
            if (parts.length < 2) return null;
            try {
                final int infoBoxCorner = Integer.parseInt(parts[1]);
                return Pair.of(headerLine.substring(0, separatorIndex) + parts[0], Corner.values()[infoBoxCorner]);
            } catch (NumberFormatException e) {
                // Fail...
            }
        }
        return null;
    }

    public static Pair<String, Corner> loadNodes(InputStream map, Collection<Node> nodes, Map<Node, Double> attributes) {
	    Pair<String, Corner> result = null;
        try (InputStreamReader ir = new InputStreamReader(map); final BufferedReader br = new BufferedReader(ir)) {
            final String headerLine = br.readLine();
            result = parseHeaderRow(headerLine);
            if (result == null) {
                return null;
            }
            // File format begins with nodes, then a single empty line and then edges,
            // then a single empty line and then attributes.
            final Map<Integer, Node> idMap = new HashMap<>();
            final Map<Node, Double> attrMap = new HashMap<>();
            final Map<Node, Double> gridAngleMap = new HashMap<>();
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
                    final NodeType type = NodeType.values()[Integer.parseInt(parts[3])];
                    final Node node = new Node(id, type);
                    if (parts.length > 4 && "F".equals(parts[4])) {
                        node.setFinish(true);
                    }
                    idMap.put(id, node);
                    coordMap.put(node, new Point(x, y));
                } else if (emptyLines == 2) {
                    final int id = Integer.parseInt(parts[0]);
                    final double attribute = Double.parseDouble(parts[1]);
                    attrMap.put(idMap.get(id), attribute);
                } else {
                    final int id = Integer.parseInt(parts[0]);
                    final double attribute = Double.parseDouble(parts[1]);
                    gridAngleMap.put(idMap.get(id), attribute);
                }
            }
            // The file format was appraently good.
            nodes.clear();
            nodes.addAll(idMap.values());
            attributes.clear();
            attrMap.forEach((node, attr) -> {
                if (node.getType() == NodeType.PIT) {
                    node.setGarage(true);
                } else if (node.isCurve()) {
                    attributes.put(node, attr);
                }
            });
            gridAngleMap.forEach(Node::setGridAngle);
            coordMap.forEach(Node::setLocation);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void setAttribute() {
	    if (selectedNode != null) {
	        if (selectedNode.getType() == NodeType.PIT) {
	            selectedNode.setGarage(!selectedNode.hasGarage());
            } else if (selectedNode.isCurve()) {
	            Double initialValue = attributes.get(selectedNode);
	            if (initialValue == null && previousCurveDistance != null) {
	                initialValue = previousCurveDistance + 0.5;
                }
                final Object attr = JOptionPane.showInputDialog(
                        this,
                        "Set Curve Distance:",
                        "Set Curve Distance",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        initialValue
                );
                if (attr != null) {
                    if (!attr.toString().isEmpty()) {
                        final Double value = Double.valueOf(attr.toString());
                        previousCurveDistance = value;
                        attributes.put(selectedNode, value);
                    } else {
                        attributes.remove(selectedNode);
                    }
                }
	        }
            repaint();
        }
    }

    private void deduceDistances() {
        final Map<Node, List<Node>> prevNodeMap = AIUtil.buildPrevNodeMap(nodes);
        nodes.stream().filter(Node::isCurve).filter(node -> prevNodeMap.get(node).stream().noneMatch(Node::isCurve)).forEach(node -> attributes.putIfAbsent(node, 0.0));
        repaint();
    }

    private void autoFillEdges() {
	    final Map<Node, Node> candidates = new HashMap<>();
	    nodes.stream()
              .filter(n -> n.getType() == NodeType.STRAIGHT)
              .forEach(n -> {
                  if (n.childCount(null) != 1) return;
                  n.childStream().findAny().ifPresent(child -> {
                      if (child.getType() != n.getType()) return;
                      candidates.put(n, child);
                  });
              });
	    final CreateEdgesItem item = new CreateEdgesItem();
	    candidates.keySet().forEach(n -> n.childStream().findAny().ifPresent(child -> {
            final int dx = child.getLocation().x - n.getLocation().x;
            final int dy = child.getLocation().y - n.getLocation().y;
            final int distSq = dx * dx + dy * dy;
            final List<Node> neighbors = candidates.keySet().stream().filter(neighbor -> {
                if (neighbor == n) return false;
                if (candidates.get(neighbor) == n) return false;
                if (candidates.get(n) == neighbor) return false;

                final int dx2 = neighbor.getLocation().x - n.getLocation().x;
                final int dy2 = neighbor.getLocation().y - n.getLocation().y;
                final int distSq2 = dx2 * dx2 + dy2 * dy2;
                return distSq2 < distSq;
            }).collect(Collectors.toList());
            if (neighbors.size() == 4) {
                neighbors.forEach(neighbor -> {
                    final Node follower = candidates.get(neighbor);
                    if (neighbors.contains(follower) && !neighbor.hasChild(n) && !n.hasChild(neighbor) && !follower.hasChild(n) && !n.hasChild(follower)) {
                        item.addEdge(new CreateEdgeItem(neighbor, n));
                        item.addEdge(new CreateEdgeItem(n, follower));
                    }
                });
            }
        }));
	    stack.execute(item);
	    repaint();
    }

    private void setGridAngle() {
        if (selectedNode != null) {
            final Double initialValue = Double.isNaN(selectedNode.getGridAngle()) ? null : selectedNode.getGridAngle();
            final Object attr = JOptionPane.showInputDialog(
                    this,
                    "Set Grid Angle:",
                    "Set Grid Angle",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    initialValue
            );
            if (attr != null && !attr.toString().isEmpty()) {
                selectedNode.setGridAngle(Double.valueOf(attr.toString()));
            } else {
                selectedNode.setGridAngle(Double.NaN);
            }
            repaint();
        }
    }

    private void setFinish() {
        if (selectedNode != null && !selectedNode.isPit()) {
            selectedNode.setFinish(!selectedNode.hasFinish());
            repaint();
        }
    }

    private void moveAll() {
        final JTextField x = new JTextField();
        final JTextField y = new JTextField();
        Object[] message = { "X:", x, "Y:", y };
        int option = JOptionPane.showConfirmDialog(null, message, "Move all nodes", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            try {
                final int dx = Integer.parseInt(x.getText());
                final int dy = Integer.parseInt(y.getText());
                if (dx != 0 || dy != 0) {
                    nodes.forEach(node -> {
                        final Point p = node.getLocation();
                        p.x += dx;
                        p.y += dy;
                    });
                    repaint();
                }
            } catch (NumberFormatException e) {
                JOptionPane.showConfirmDialog(this, "Error: " + e.getMessage(), "Invalid number format", JOptionPane.DEFAULT_OPTION);
            }
        }
    }

    private void scale() {
        final Object attr = JOptionPane.showInputDialog(
                this,
                "Scaling factor:",
                "Set Scaling Factor",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                1.0
        );
        if (attr != null && !attr.toString().isEmpty()) {
            try {
                final double newScale = Double.valueOf(attr.toString());
                if (newScale != 1.0 && newScale > 0) {
                    nodes.forEach(node -> {
                        final Point p = node.getLocation();
                        p.x *= newScale;
                        p.y *= newScale;
                    });
                    repaint();
                }
            } catch (NumberFormatException e) {
                JOptionPane.showConfirmDialog(this, "Error: " + e.getMessage(), "Invalid number format", JOptionPane.DEFAULT_OPTION);
            }
        }
    }

    private void unifyNodeIdentifiers() {
        try {
            select(null);
            TrackData.build(nodes, attributes);
            nodes.sort(Comparator.reverseOrder());
            final List<Node> newNodes = new ArrayList<>();
            final Map<Node, Double> newAttributes = new HashMap<>();
            final Map<Node, Node> oldToNew = new HashMap<>();
            for (Node node : nodes) {
                final Double attr = attributes.get(node);
                final Node newNode = new Node(newNodes.size(), node.getType());
                newNode.setGarage(node.hasGarage());
                newNode.setFinish(node.hasFinish());
                newNode.setLocation(node.getLocation());
                newNode.setGridAngle(node.getGridAngle());
                if (attr != null) newAttributes.put(newNode, attr);
                oldToNew.put(node, newNode);
                newNodes.add(newNode);
            }
            oldToNew.forEach((oldNode, newNode) -> oldNode.childStream().forEach(oldChild -> newNode.addChild(oldToNew.get(oldChild))));
            oldToNew.clear();
            nodes.clear();
            nodes.addAll(newNodes);
            attributes.clear();
            attributes.putAll(newAttributes);
            repaint();
        } catch (Exception e) {
            JOptionPane.showConfirmDialog(this, "Failed to unify node identifiers: " + e.getMessage(), "Validation Error", JOptionPane.DEFAULT_OPTION);
        }
    }

    private void showIdentifiers() {
	    showDistances = false;
	    showLanes = false;
	    showIdentifiers = !showIdentifiers;
	    repaint();
    }

    private void showDistances() {
	    if (!showDistances && !showLanes) {
	        try {
                TrackData.updateDistances(nodes, attributes);
            } catch (Exception e) {
                JOptionPane.showConfirmDialog(this, "Error when calculating distances: " + e.getMessage(), "Distance calculation error", JOptionPane.DEFAULT_OPTION);
            }
        }
        showIdentifiers = false;
	    showLanes = false;
	    showDistances = !showDistances;
	    repaint();
    }

    private void showLanes() {
        if (!showLanes && !showDistances) {
            try {
                TrackData.updateDistances(nodes, attributes);
            } catch (Exception e) {
                // No need to report an error. This is visualization tool.
            }
        }
        if (!showLanes) {
            try {
                // Calculate lanes
                List<Node> sortedNodes = nodes
                        .stream()
                        .filter(n -> n.getDistance() >= 0.0)
                        .filter(n -> n.getType() != NodeType.PIT)
                        .sorted(Comparator.comparingInt(n1 -> TrackLanes.distanceToInt(n1.getDistance())))
                        .collect(Collectors.toList());
                final List<Node> finishLine = sortedNodes.subList(0, 3);
                if (finishLine.stream().anyMatch(n -> !n.hasFinish())) {
                    throw new RuntimeException("Finish line nodes don't have the lowest distance");
                }
                // Middle node is either 1st node (distance 0.0) or 3rd node (distance 0.5)
                final int middleIndex = finishLine.get(0).getDistance() == finishLine.get(1).getDistance() ? 2 : 0;
                final Map<Node, Set<Node>> collisionMap = new HashMap<>();
                lanes[0] = new TrackLanes.Lane(finishLine.get(middleIndex == 2 ? 0 : 1), collisionMap);
                lanes[1] = new TrackLanes.Lane(finishLine.get(middleIndex), collisionMap);
                lanes[2] = new TrackLanes.Lane(finishLine.get(middleIndex == 2 ? 1 : 2), collisionMap);

                sortedNodes = sortedNodes.subList(3, sortedNodes.size());
                sortedNodes.forEach(node -> {
                    final TrackLanes.Lane matchingLane = Arrays
                            .stream(lanes)
                            .filter(lane -> lane.canContinueTo(node))
                            .min(Comparator.comparingInt(l -> TrackLanes.distanceToInt(l.getDistance())))
                            .orElse(null);
                    if (matchingLane == null) {
                        throw new RuntimeException("Internal error when calculating lanes: " + node.getId());
                    }
                    matchingLane.addNode(node);
                });
            } catch (Exception e) {
                JOptionPane.showConfirmDialog(this, "Error when calculating lanes: " + e.getMessage(), "Lane calculation error", JOptionPane.DEFAULT_OPTION);
            }
        }
        showDistances = false;
        showIdentifiers = false;
        showLanes = !showLanes;
        if (lanes[0] == null || lanes[1] == null || lanes[2] == null) {
            showLanes = false;
        }
        repaint();
    }

    private void validateTrack() {
	    try {
            final List<Node> grid = TrackData.build(nodes, attributes);
            if (grid.size() < 10) {
                JOptionPane.showConfirmDialog(this, "Track validation failed: Starting grid has less than 10 spots", "Validation Error", JOptionPane.DEFAULT_OPTION);
                return;
            }
            TrackLanes.buildCollisionMap(nodes);
            JOptionPane.showConfirmDialog(this, "Track seems OK", "Success", JOptionPane.DEFAULT_OPTION);
        } catch (Exception e) {
            JOptionPane.showConfirmDialog(this, "Track validation failed: " + e.getMessage(), "Validation Error", JOptionPane.DEFAULT_OPTION);
        }
    }

    private void switchInfoBoxCorner() {
	    final Corner[] corners = Corner.values();
	    infoBoxCorner = corners[(infoBoxCorner.ordinal() + 1) % corners.length];
	    repaint();
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
            case BLOCKED:
                color = Color.WHITE;
                break;
            case FINISH:
                color = Color.GREEN;
                break;
            case CURVE_3:
                color = new Color(0xAA0000);
                break;
            case PIT:
                color = Color.BLACK;
                break;
            default:
                throw new RuntimeException("Unknown node type");
        }
        final Point p = node.getLocation();
        final double angle = node.getGridAngle();
        if (Double.isNaN(angle)) {
            drawOval(g2d, p.x, p.y, DIAMETER, DIAMETER, true, color, 0);
        } else {
            Player.draw(g2d, p.x, p.y, angle / 180 * Math.PI, Color.BLUE, Color.BLACK, 1.0);
        }
        final Double attr = attributes.get(node);
        if (attr != null || node.hasGarage()) {
            // Draw attribute indicator
            drawOval(g2d, p.x, p.y, 4, 4, true, Color.YELLOW, 0);
        }
        if (node.hasFinish()) {
            drawOval(g2d, p.x, p.y, DIAMETER + 1, DIAMETER + 1, false, Color.BLUE, 2);
        }
    }

    private void drawArcs(Graphics2D g2d, Collection<Node> nodes) {
        final Color tmpC = g2d.getColor();
        for (Node node : nodes) {
            final Point p = node.getLocation();
            node.forEachChild(child -> {
                final Point np = child.getLocation();
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

    static void drawOval(Graphics2D g2d, int x, int y, int width, int height, boolean filled, Color color, int lineThickness) {
        // Store before changing.
        final Stroke tmpS = g2d.getStroke();
        final Color tmpC = g2d.getColor();

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(lineThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        final int x2 = x - (width / 2);
        final int y2 = y - (height / 2);

        if (filled) g2d.fillOval(x2, y2, width, height);
        else g2d.drawOval(x2, y2, width, height);

        // Set values to previous when done.
        g2d.setColor(tmpC);
        g2d.setStroke(tmpS);
    }

    @Nullable
    static Node getNode(Collection<Node> nodes, int x, int y, int threshold) {
        for (Node node : nodes) {
            final Point point = node.getLocation();
            if (Math.hypot(x - point.x, y - point.y) < threshold) {
                return node;
            }
        }
        return null;
    }

    static class Filter extends FileFilter {
        final static String[] imageExtensions = {"jpeg", "jpg", "gif", "tiff", "tif", "png"};
        final static String[] dataExtensions = {"dat"};
        final static String[] templateExtensions = {"cst"};

        private final String[] extensions;
        private final String description;

        Filter(String[] extensions, String description) {
            this.extensions = extensions;
            this.description = description;
        }

        @Override
        public boolean accept(File f) {
            if (f.isDirectory()) {
                return true;
            }
            String extension = getExtension(f);
            if (extension != null) {
                for (String allowedExtension : extensions) {
                    if (extension.equals(allowedExtension)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public String getDescription() {
            return description;
        }

        String getExtension(File f) {
            String ext = null;
            String s = f.getName();
            int i = s.lastIndexOf('.');
            if (i > 0 &&  i < s.length() - 1) {
                ext = s.substring(i+1).toLowerCase();
            }
            return ext;
        }
    }

    @Override
    public String getName() {
	    return "Map Editor";
    }
}
