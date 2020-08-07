package gp;

import gp.ai.*;
import gp.ai.Node;
import gp.model.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Client extends Game implements Runnable {
    private final Socket socket;
    private final ObjectOutputStream oos;
    private final ObjectInputStream ois;
    private AI ai;
    private Player current;
    private Player controlledPlayer;
    private final Map<String, Player> playerMap = new HashMap<>();
    private final Profile profile;
    private boolean initialStandingsReceived;
    private boolean waiting;

    public Client(JFrame frame, Socket socket, JPanel panel, Profile profile) throws IOException {
        super(frame, panel);
        this.profile = profile;
        this.socket = socket;
        oos = new ObjectOutputStream(socket.getOutputStream());
        ois = new ObjectInputStream(socket.getInputStream()); // This may block if connection cannot be established!
        setPreferredSize(new Dimension(400, 200));
    }

    @Override
    public void run() {
        while (socket.isConnected()) {
            try {
                Object request = ois.readObject();
                if (request instanceof Notification) {
                    ((Notification) request).notify(this);
                } else if (request instanceof Standings) {
                    final Standings standings = (Standings) request;
                    this.standings = Arrays.stream(standings.getPlayerIds()).map(immutablePlayerMap::get).collect(Collectors.toList());
                } else if (request instanceof FinalStandings) {
                    final FinalStandings standings = (FinalStandings) request;
                    if (!initialStandingsReceived) {
                        profile.standingsReceived(standings.getStats(), data.getTrackId(), standings.isSingleRace());
                        profile.getManager().saveProfiles();
                        initialStandingsReceived = true;
                        immutablePlayerMap = new HashMap<>(playerMap);
                        this.standings = Arrays.stream(standings.getStats()).map(ps -> ps.playerId).map(immutablePlayerMap::get).collect(Collectors.toList());
                        waiting = false;
                        repaint();
                        continue;
                    }
                    finalStandings = standings.getStats();
                    profile.standingsReceived(finalStandings, null, standings.isSingleRace());
                    profile.getManager().saveProfiles();
                    repaint();
                    break;
                } else {
                    try {
                        if (request instanceof GameState) {
                            final GameState gameState = (GameState) request;
                            roll = null;
                            setCurrent(controlledPlayer);
                            repaint();
                            oos.writeObject(ai.selectGear(gameState));
                            updateHitpointMap(gameState);
                        } else if (request instanceof Moves) {
                            final Moves moves = (Moves) request;
                            oos.writeObject(ai.selectMove(moves));
                        } else if (request instanceof TrackData) {
                            try {
                                waiting = true;
                                initTrack((TrackData) request);
                                final AI backupAI = new BeginnerAI(data);
                                ai = new ManualAI(backupAI, frame, this, profile, data);
                            } catch (Exception e) {
                                final String msg = "Error when receiving track data: " + e.getMessage();
                                Main.log.log(Level.SEVERE, msg, e);
                                JOptionPane.showConfirmDialog(this, msg, "Error", JOptionPane.DEFAULT_OPTION);
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
                        Main.log.log(Level.SEVERE, "Error when sending response to server", e);
                        break;
                    }
                }
            } catch (EOFException e) {
                // This is ok, no objects to read
            } catch (IOException | ClassNotFoundException e) {
                Main.log.log(Level.SEVERE, "Error when reading object input from server", e);
                break;
            }
        }
        if (socket.isClosed()) {
            return;
        }
        if (finalStandings == null) {
            JOptionPane.showConfirmDialog(this, "Connection to server lost", "Error", JOptionPane.DEFAULT_OPTION);
            exit();
        } else {
            clickToExit();
        }
    }

    @Override
    protected void exit() {
        try {
            ois.close();
        } catch (IOException e) {
            Main.log.log(Level.WARNING, "Error when closing server connection", e);
        }
        try {
            oos.close();
        } catch (IOException e) {
            Main.log.log(Level.WARNING, "Error when closing server connection", e);
        }
        try {
            socket.close();
        } catch (IOException e) {
            Main.log.log(Level.WARNING, "Error when closing server connection", e);
        }
        super.exit();
    }

    public void notify(MovementNotification notification) {
        final Player player = immutablePlayerMap.get(notification.getPlayerId());
        if (player != null) {
            player.move(nodes.get(notification.getNodeId()));
        }
    }

    public void notify(RollNotification notification) {
        final Player player = immutablePlayerMap.get(notification.getPlayerId());
        if (player != null) {
            setCurrent(player);
            roll = notification.getRoll();
            player.setGear(notification.getGear());
            repaint();
        }
    }

    public void notify(HitpointNotification notification) {
        final Player player = immutablePlayerMap.get(notification.getPlayerId());
        if (player != null) {
            if (player.hitpoints != notification.getHitpoints()) {
                scheduleHitpointAnimation(player.hitpoints - notification.getHitpoints(), player);
            }
            player.setHitpoints(notification.getHitpoints());
        }
    }

    public void notify(LapChangeNotification notification) {
        final Player player = immutablePlayerMap.get(notification.getPlayerId());
        if (player != null) {
            player.setLapsRemaining(notification.getLapsRemaining());
        }
    }

    public void notify(CurveStopNotification notification) {
        final Player player = immutablePlayerMap.get(notification.getPlayerId());
        if (player != null) {
            player.setCurveStops(notification.getCurveStops());
        }
    }

    public void notify(CreatedPlayerNotification notification) {
        final Node startNode = nodes.get(notification.getNodeId());
        final Player player = new Player(notification.getPlayerId(), startNode, notification.getGridAngle(), this, notification.getColor1(), notification.getColor2());
        player.setName(notification.getName());
        player.setHitpoints(notification.getHitpoints());
        player.setLapsRemaining(notification.getLapsRemaining());
        if (notification.isControlled()) {
            if (controlledPlayer != null) {
                Main.log.log(Level.SEVERE, "Client assigneed to control multiple players");
            }
            controlledPlayer = player;
        }
        playerMap.put(notification.getPlayerId(), player);
    }

    private void setCurrent(Player player) {
        if (current != null) {
            current.clearRoute();
        }
        if (current != player) {
            current = player;
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
        } else {
            super.drawStandings(g2d);
        }
    }

    @Override
    protected Player getCurrent() {
        return current;
    }
}
