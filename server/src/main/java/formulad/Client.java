package formulad;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import formulad.ai.*;
import formulad.ai.Node;
import formulad.model.*;
import formulad.model.Gear;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Client extends Screen implements Runnable {
    private final JFrame frame;
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
    private boolean highlightCurrentPlayer;

    private PlayerId playerId;
    private GameState gameState;
    private Moves moves;
    private LocalPlayer player;
    private List<LocalPlayer> allPlayers = new ArrayList<>();
    private final Socket socket;
    private final ObjectOutputStream oos;
    private final ObjectInputStream ois;
    private final AI ai;

    public Client(JFrame frame, Socket socket) throws IOException {
        this.frame = frame;
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
        ois = new ObjectInputStream(socket.getInputStream());
        ai = new GreatAI();
        //final AI backupAI = new GreatAI();
        //ai = new ManualAI(backupAI, frame, this);
    }

    @Override
    public void run() {
        try {
            while (socket.isConnected()) {
                try {
                    Object request = ois.readObject();
                    if (request instanceof Track) {
                        oos.writeObject(ai.startGame((Track) request));
                    } else if (request instanceof GameState) {
                        oos.writeObject(ai.selectGear((GameState) request));
                    } else if (request instanceof Moves) {
                        oos.writeObject(ai.selectMove((Moves) request));
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

    void updatePlayerId(PlayerId playerId) {
        this.playerId = playerId;
    }

    void updateGameState(GameState gameState) {
        if (this.gameState == null) {
            for (PlayerState playerState : gameState.getPlayers()) {
                final LocalPlayer player = new LocalPlayer(playerState.getPlayerId(), nodes.get(playerState.getNodeId()), 0, 1, this, playerState.getLeeway());
                if (playerId.getPlayerId().equals(playerState.getPlayerId())) {
                    this.player = player;
                }
                allPlayers.add(player);
            }
        }
        this.gameState = gameState;
    }

    void updateMoves(Moves moves) {
        this.moves = moves;
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
        g2d.fillRect(getWidth() - 250, 0, 249, 5 + 15 * allPlayers.size());
        g2d.setColor(Color.BLACK);
        g2d.drawRect(getWidth() - 250, 0, 249, 5 + 15 * allPlayers.size());
        int i = 0;
        for (LocalPlayer player : allPlayers) {
            if (this.player == player) {
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
        if (gameState != null) {
            for (LocalPlayer player : allPlayers) {
                if (this.player == player) {
                    player.drawRoll(g2d, roll);
                    if (highlightCurrentPlayer) {
                        player.highlight(g2d, coordinates);
                    }
                }
                player.draw(g2d, coordinates);
            }
        }
    }
}
