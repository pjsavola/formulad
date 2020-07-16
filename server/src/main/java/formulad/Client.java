package formulad;

import formulad.ai.*;
import formulad.ai.Node;
import formulad.model.*;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.logging.Level;

public class Client extends Game implements Runnable {
    private final Socket socket;
    private final ObjectOutputStream oos;
    private final ObjectInputStream ois;
    private final AI ai;
    private Integer roll;
    private Player current;
    private Player controlledPlayer;
    private String controlledPlayerId;
    private final Map<String, Player> playerMap = new HashMap<>();
    private final List<Player> standings = new ArrayList<>();
    private final Profile profile;
    private boolean initialStandingsReceived;
    private boolean waiting;

    public Client(JFrame frame, Socket socket, JPanel panel, Profile profile) throws IOException {
        super(frame, panel);
        this.profile = profile;
        this.socket = socket;
        oos = new ObjectOutputStream(socket.getOutputStream());
        ois = new ObjectInputStream(socket.getInputStream()); // This may block if connection cannot be established!
        final AI backupAI = new GreatAI();
        ai = new ManualAI(backupAI, frame, this, profile);
    }

    @Override
    public void run() {
        try {
            while (socket.isConnected()) {
                try {
                    Object request = ois.readObject();
                    if (request instanceof Notification) {
                        ((Notification) request).notify(this);
                    } else if (request instanceof Standings) {
                        final Standings standings = (Standings) request;
                        synchronized (this.standings) {
                            this.standings.clear();
                            for (String id : standings.getPlayerIds()) {
                                final Player player = playerMap.get(id);
                                if (player != null) {
                                    this.standings.add(player);
                                }
                            }
                        }
                    } else if (request instanceof FinalStandings) {
                        final FinalStandings standings = (FinalStandings) request;
                        if (!initialStandingsReceived) {
                            profile.standingsReceived(standings.getStats(), true);
                            initialStandingsReceived = true;
                            synchronized (this.standings) {
                                for (PlayerStats stats : standings.getStats()) {
                                    final Player player = playerMap.get(stats.playerId);
                                    if (player != null) {
                                        this.standings.add(player);
                                    }
                                }
                            }
                            continue;
                        }
                        finalStandings = standings.getStats();
                        profile.standingsReceived(finalStandings, false);
                        repaint();
                        break;
                    } else {
                        try {
                            if (request instanceof Track) {
                                final Track track = (Track) request;
                                controlledPlayerId = track.getPlayer().getPlayerId();
                                oos.writeObject(ai.startGame(track));
                            } else if (request instanceof GameState) {
                                final GameState gameState = (GameState) request;
                                roll = null;
                                setCurrent(controlledPlayer);
                                oos.writeObject(ai.selectGear(gameState));
                                waiting = false;
                                repaint();
                            } else if (request instanceof Moves) {
                                final Moves moves = (Moves) request;
                                oos.writeObject(ai.selectMove(moves));
                            } else if (request instanceof ProfileRequest) {
                                final String trackId = ((ProfileRequest) request).getTrackId();
                                try {
                                    waiting = true;
                                    initTrack(trackId);
                                } catch (RuntimeException e) {
                                    FormulaD.log.log(Level.SEVERE, "Track " + trackId + " not found", e);
                                    JOptionPane.showConfirmDialog(this, "Track " + trackId + " not found", "Error", JOptionPane.DEFAULT_OPTION);
                                    exit();
                                    return;
                                }
                                oos.writeObject(new ProfileMessage(profile));
                            } else if (request instanceof Kick) {
                                JOptionPane.showConfirmDialog(this, "You have been kicked", "Oops", JOptionPane.DEFAULT_OPTION);
                                exit();
                                return;
                            }
                        } catch (IOException e) {
                            FormulaD.log.log(Level.SEVERE, "Error when sending response to server", e);
                            break;
                        }
                    }
                } catch (EOFException e) {
                    // This is ok, no objects to read
                } catch (IOException | ClassNotFoundException e) {
                    FormulaD.log.log(Level.SEVERE, "Error when reading object input from server", e);
                    break;
                }
            }
            if (finalStandings == null) {
                JOptionPane.showConfirmDialog(this, "Connection to server lost", "Error", JOptionPane.DEFAULT_OPTION);
                exit();
            } else {
                clickToExit();
            }
            ois.close();
            oos.close();
            socket.close();
        } catch (IOException e) {
            FormulaD.log.log(Level.WARNING, "Error when closing server connection", e);
            exit();
        }
    }

    public void notify(MovementNotification notification) {
        final Player player = playerMap.get(notification.getPlayerId());
        if (player != null) {
            player.move(nodes.get(notification.getNodeId()), coordinates);
        }
    }

    public void notify(RollNotification notification) {
        final Player player = playerMap.get(notification.getPlayerId());
        if (player != null) {
            setCurrent(player);
            roll = notification.getRoll();
            player.setGear(notification.getGear());
        }
    }

    public void notify(HitpointNotification notification) {
        final Player player = playerMap.get(notification.getPlayerId());
        if (player != null) {
            player.setHitpoints(notification.getHitpoints());
        }
    }

    public void notify(LapChangeNotification notification) {
        final Player player = playerMap.get(notification.getPlayerId());
        if (player != null) {
            player.setLapsRemaining(notification.getLapsRemaining());
        }
    }

    public void notify(CurveStopNotification notification) {
        final Player player = playerMap.get(notification.getPlayerId());
        if (player != null) {
            player.setCurveStops(notification.getCurveStops());
        }
    }

    public void notify(CreatedPlayerNotification notification) {
        final Node startNode = nodes.get(notification.getNodeId());
        final Player player = new Player(notification.getPlayerId(), startNode, attributes.get(startNode), this);
        player.setName(notification.getName());
        player.setHitpoints(notification.getHitpoints());
        player.setLapsRemaining(notification.getLapsRemaining());
        if (notification.getPlayerId().equals(controlledPlayerId)) {
            controlledPlayer = player;
        }
        synchronized (playerMap) {
            playerMap.put(notification.getPlayerId(), player);
        }
        repaint();
    }

    private void setCurrent(Player player) {
        if (current != player) {
            if (current != null) {
                current.clearRoute();
            }
            current = player;
            repaint();
        }
    }

    @Override
    protected void drawStandings(Graphics2D g2d) {
        if (waiting) {
            final int x = getWidth() / 2 - 200;
            final int y = getHeight() / 2 - 100;
            g2d.setColor(Color.GRAY);
            g2d.fillRect(x, y, 400, 200);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x, y, 400, 200);
            final String text = "Waiting for race to begin!";
            final int titleWidth = g2d.getFontMetrics().stringWidth(text);
            g2d.drawString(text, x + 200 - titleWidth / 2, y + 100);
        }
        if (finalStandings != null) {
            final int height = 5 + 15 * (finalStandings.length + 1);
            final int x = getWidth() / 2 - 200;
            final int y = getHeight() / 2 - height / 2;
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            final int titleWidth = g2d.getFontMetrics().stringWidth("STANDINGS");
            g2d.drawString("STANDINGS", x + 200 - titleWidth / 2, y - 20);
            g2d.setColor(Color.GRAY);
            g2d.fillRect(x, y, 400, height);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x, y, 400, height);
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.drawString("Name", x + 30, y + 15);
            g2d.drawString("HP", x + 140, y + 15);
            g2d.drawString("Turns", x + 190, y + 15);
            g2d.drawString("Grid", x + 230, y + 15);
            g2d.drawString("Time", x + 270, y + 15);
            g2d.setFont(new Font("Arial", Font.PLAIN, 12));
            for (int i = 0; i < finalStandings.length; ++i) {
                final PlayerStats stats = finalStandings[i];
                synchronized (playerMap) {
                    final Player player = playerMap.get(stats.playerId);
                    final String name = player.getName();
                    player.draw(g2d, x + 15, y + (i + 1) * 15 + 10, 0);
                    g2d.setColor(Color.BLACK);
                    g2d.drawString(name, x + 30, y + (i + 1) * 15 + 15);
                    final String hp = stats.hitpoints > 0 ? Integer.toString(stats.hitpoints) : "DNF";
                    g2d.drawString(hp, x + 140, y + (i + 1) * 15 + 15);
                    g2d.drawString(Integer.toString(stats.turns), x + 190, y + (i + 1) * 15 + 15);
                    g2d.drawString(Integer.toString(stats.gridPosition), x + 230, y + (i + 1) * 15 + 15);
                    final long timeUsed = stats.timeUsed / 100;
                    final double timeUsedSecs = timeUsed / 10.0;
                    g2d.drawString(Double.toString(timeUsedSecs), x + 270, y + (i + 1) * 15 + 15);
                }
            }
        }
    }

    @Override
    protected void drawInfoBox(Graphics2D g2d) {
        int playerCount;
        synchronized (playerMap) {
            playerCount = playerMap.size();
        }
        g2d.setColor(Color.GRAY);
        g2d.fillRect(getWidth() - 250, 0, 249, 5 + 15 * playerCount);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(getWidth() - 250, 0, 249, 5 + 15 * playerCount);
        int i = 0;
        synchronized (standings) {
            for (Player player : standings) {
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
    }

    @Override
    protected void drawPlayers(Graphics2D g2d) {
        if (current != null) {
            if (roll != null) {
                current.drawRoll(g2d, roll);
            } else if (current == controlledPlayer) {
                current.highlight(g2d, coordinates);
            }
        }
        synchronized (playerMap) {
            playerMap.values().forEach(player -> player.draw(g2d, coordinates));
        }
    }
}
