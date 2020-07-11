package formulad;

import formulad.ai.*;
import formulad.ai.Node;
import formulad.model.*;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;

public class Client extends Screen implements Runnable {
    // Node identifier equals to the index in this array
    private final List<Node> nodes = new ArrayList<>();
    private Map<Node, Point> coordinates = new HashMap<>();
    private BufferedImage backgroundImage;
    private Integer roll;
    private final Map<Node, Double> distanceMap = new HashMap<>();
    private boolean stopped;
    private static final String gameId = "Sebring";
    @Nullable
    private Map<Integer, Integer> highlightedNodeToDamage;

    private PlayerId playerId;
    private GameState gameState;
    private Moves moves;
    private Map<String, Player> playerMap = new HashMap<>();
    private final Socket socket;
    private final ObjectOutputStream oos;
    private final ObjectInputStream ois;
    private final AI ai;
    private Player current;
    private Player controlledPlayer;

    public Client(JFrame frame, Socket socket) throws IOException {
        backgroundImage = ImageCache.getImage("/sebring.jpg");
        setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        // Contains angles for start nodes and distance information for curves
        final Map<Node, Double> attributes = new HashMap<>();
        try (InputStream is = Client.class.getResourceAsStream("/sebring.dat")) {
            MapEditor.loadNodes(is, nodes, attributes, coordinates);
        } catch (IOException e) {
            throw new RuntimeException("Cannot be bothered to work this out", e);
        }

        this.socket = socket;
        oos = new ObjectOutputStream(socket.getOutputStream());
        ois = new ObjectInputStream(socket.getInputStream()); // This may block if connection cannot be established!
        final AI backupAI = new GreatAI();
        ai = new ManualAI(backupAI, frame, this);
    }

    @Override
    public void run() {
        try {
            while (socket.isConnected()) {
                try {
                    Object request = ois.readObject();
                    if (request instanceof MovementNotification) {
                        final MovementNotification movement = (MovementNotification) request;
                        carMoved(movement);
                    } else if (request instanceof RollNotification) {
                        final RollNotification roll = (RollNotification) request;
                        diceRolled(roll);
                    } else if (request instanceof HitpointNotification) {
                        final HitpointNotification damage = (HitpointNotification) request;
                        carDamaged(damage);
                    } else if (request instanceof Track) {
                        final Track track = (Track) request;
                        updateTrack(track);
                        oos.writeObject(ai.startGame(track));
                    } else if (request instanceof GameState) {
                        final GameState gameState = (GameState) request;
                        updateGameState(gameState);
                        roll = null;
                        setCurrent(controlledPlayer);
                        oos.writeObject(ai.selectGear(gameState));
                    } else if (request instanceof Moves) {
                        final Moves moves = (Moves) request;
                        updateMoves(moves);
                        oos.writeObject(ai.selectMove(moves));
                    }
                } catch (EOFException e) {
                    // This is ok, no response yet
                }
            }
            ois.close();
            oos.close();
            socket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    void updateTrack(Track track) {
        this.playerId = track.getPlayer();
    }

    void updateGameState(GameState gameState) {
        if (this.gameState == null) {
            for (PlayerState playerState : gameState.getPlayers()) {
                final Player player = new Player(playerState.getPlayerId(), nodes.get(playerState.getNodeId()), 0, 1, this);
                player.setName("Opponent");
                if (playerId.getPlayerId().equals(playerState.getPlayerId())) {
                    this.playerId = playerId;
                    player.setName("You");
                    controlledPlayer = player;
                }
                playerMap.put(playerState.getPlayerId(), player);
            }
        }
        this.gameState = gameState;
        repaint();
    }

    void updateMoves(Moves moves) {
        this.moves = moves;
    }

    void carMoved(MovementNotification notification) {
        final Player player = playerMap.get(notification.getPlayerId());
        if (player != null) {
            player.move(nodes.get(notification.getNodeId()), coordinates);
        }
    }

    void diceRolled(RollNotification notification) {
        final Player player = playerMap.get(notification.getPlayerId());
        if (player != null) {
            setCurrent(player);
            roll = notification.getRoll();
            player.setGear(notification.getGear());
        }
    }

    void carDamaged(HitpointNotification notification) {
        final Player player = playerMap.get(notification.getPlayerId());
        if (player != null) {
            player.setHitpoints(notification.getHitpoints());
        }
    }

    void setCurrent(Player player) {
        if (current != player) {
            if (current != null) {
                current.clearRoute();
            }
            current = player;
            repaint();
        }
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
        final Node target = MapEditor.getNode(nodes, coordinates, x, y, MapEditor.DIAMETER);
        return target == null ? null : target.getId();
    }

    @Override
    public void paintComponent(Graphics g) {
	    if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, null);
        }
        final Graphics2D g2d = (Graphics2D) g;
	    // Circle for dice rolls
        MapEditor.drawOval(g2d, 40, 40, 50, 50, true, true, Color.BLACK, 1);
        drawTargets(g2d);
        drawInfoBox(g2d);
        drawPlayers(g2d);
    }

    private void drawTargets(Graphics2D g2d) {
        if (highlightedNodeToDamage != null) {
            for (Map.Entry<Integer, Integer> entry : highlightedNodeToDamage.entrySet()) {
                final int nodeId = entry.getKey();
                if (nodeId < 0 || nodeId >= nodes.size()) continue;
                final Node node = nodes.get(nodeId);
                final int damage = entry.getValue();
                final Point p = coordinates.get(node);
                if (damage > 0) {
                    g2d.setFont(new Font("Arial", Font.PLAIN, 9));
                    g2d.setColor(Color.RED);
                    final int x = p.x - (damage >= 10 ? 5 : 2);
                    g2d.drawString(Integer.toString(damage), x, p.y + 3);
                }
                MapEditor.drawOval(g2d, p.x, p.y, 12, 12, true, false, Color.YELLOW, 1);
            }
        }
    }

    private void drawInfoBox(Graphics2D g2d) {
        g2d.setColor(Color.GRAY);
        g2d.fillRect(getWidth() - 250, 0, 249, 5 + 15 * playerMap.size());
        g2d.setColor(Color.BLACK);
        g2d.drawRect(getWidth() - 250, 0, 249, 5 + 15 * playerMap.size());
        int i = 0;
        for (Player player : playerMap.values()) {
            if (current != null && current == player) {
                // Turn marker
                g2d.setColor(Color.RED);
                g2d.fillPolygon(new int[] { getWidth() - 252, getWidth() - 257, getWidth() - 257 }, new int[] { i * 15 + 10, i * 15 + 7, i * 15 + 13 }, 3);
            }
            player.draw(g2d, getWidth() - 235, i * 15 + 10, 0);
            player.drawStats(g2d, getWidth() - 220, i * 15 + 15);
            i++;
        }
    }

    private void drawPlayers(Graphics2D g2d) {
        if (current != null) {
            if (roll != null) {
                current.drawRoll(g2d, roll);
            } else if (current == controlledPlayer) {
                current.highlight(g2d, coordinates);
            }
        }
        playerMap.values().forEach(player -> player.draw(g2d, coordinates));
    }
}
