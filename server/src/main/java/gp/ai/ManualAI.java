package gp.ai;

import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;

import gp.Profile;
import gp.Game;
import gp.model.*;
import gp.model.Gear;
import org.apache.commons.lang3.mutable.MutableInt;

public class ManualAI implements AI {

    private String playerId;
    private int hitpoints;
    private int gear;
    private Node location;
    private final AI ai;
    private final JFrame frame;
    private final Game game;
    private boolean automaticMove;
    private static final int listenerDelay = 50;
    private final Profile profile;
    private boolean initialStandingsReceived;
    private String trackId;
    private Map<Integer, Node> nodeMap;
    public volatile boolean interrupted;

    public ManualAI(AI ai, JFrame frame, Game game, Profile profile) {
        this.ai = ai;
        this.frame = frame;
        this.game = game;
        this.profile = profile;
    }

    @Override
    public NameAtStart startGame(Track track) {
        trackId = track.getGame().getGameId();
        this.playerId = track.getPlayer().getPlayerId();
        nodeMap = AIUtil.buildNodeMap(track.getTrack().getNodes(), track.getTrack().getEdges());
        return ai.startGame(track).name(profile.getName()).id(profile.getId());
    }

    @Override
    public Gear selectGear(GameState gameState) {
        for (PlayerState playerState : gameState.getPlayers()) {
            if (playerId.equals(playerState.getPlayerId())) {
                hitpoints = playerState.getHitpoints();
                gear = playerState.getGear();
                location = nodeMap.get(playerState.getNodeId());
                break;
            }
        }
        if (hitpoints == 0) {
            throw new RuntimeException("No data sent for player: " + playerId);
        }
        final MutableInt selectedGear = new MutableInt(0);
        final boolean inPits = location != null && location.getType() == NodeType.PIT;
        game.actionMenu.removeAll();
        for (int newGear = 1; newGear <= 6; ++newGear) {
            if (AIUtil.validateGear(hitpoints, gear, newGear, inPits)) {
                final int[] distribution = gp.ai.Gear.getDistribution(newGear);
                String label = "Gear " + newGear + " (" + distribution[0] + "-" + distribution[distribution.length -1] + ")";
                if (gear > newGear + 1) {
                    label += " -" + (gear - newGear - 1) + " HP";
                }
                final MenuItem item = new MenuItem(label);
                item.setShortcut(new MenuShortcut(KeyEvent.VK_0 + newGear));
                final int finalNewGear = newGear;
                item.addActionListener(e -> {
                    selectedGear.setValue(finalNewGear);
                });
                game.actionMenu.add(item);
            }
        }
        final KeyListener keyListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                final char c = e.getKeyChar();
                if (c == 'a') {
                    final Gear gear = ai.selectGear(gameState);
                    if (gear != null) {
                        automaticMove = true;
                        selectedGear.setValue(gear.getGear());
                    }
                } else if (c >= '1' && c <= '6') {
                    if (AIUtil.validateGear(hitpoints, gear, c - '0', inPits)) {
                        selectedGear.setValue(c - '0');
                    }
                } else if (c == 'd') {
                    game.debug = !game.debug;
                    game.repaint();
                }
            }
        };
        frame.addKeyListener(keyListener);
        frame.requestFocus();
        while (!interrupted) {
            try {
                Thread.sleep(listenerDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final int newGear = selectedGear.getValue();
            if (newGear != 0) {
                frame.removeKeyListener(keyListener);
                game.actionMenu.removeAll();
                return new Gear().gear(newGear);
            }
        }
        return new Gear().gear(gear);
    }

    @Override
    public SelectedIndex selectMove(Moves allMoves) {
        if (automaticMove) {
            automaticMove = false;
            return ai.selectMove(allMoves);
        }
        final Map<Integer, Map<Integer, Integer>> brakingMap = new HashMap<>();
        final Map<Integer, Map<Integer, Integer>> indexMap = new HashMap<>();
        for (int i = 0; i < allMoves.getMoves().size(); i++) {
            final ValidMove vm = allMoves.getMoves().get(i);
            final int nodeId = vm.getNodeId();
            final int overshootDamage = vm.getOvershoot();
            final int brakingDamage = vm.getBraking();
            brakingMap.computeIfAbsent(brakingDamage, _key -> new HashMap<>()).put(nodeId, brakingDamage + overshootDamage);
            indexMap.computeIfAbsent(brakingDamage, _key -> new HashMap<>()).put(nodeId, i);
        }
        // Automatically use brakes if there are no valid moves without
        int minBraking = 0;
        while (!brakingMap.containsKey(minBraking)) {
            ++minBraking;
        }
        final int finalMinBraking = minBraking;
        game.highlightNodes(brakingMap.get(finalMinBraking));
        final MutableInt braking = new MutableInt(finalMinBraking);
        game.actionMenu.removeAll();
        final MenuItem item1 = new MenuItem("Brake more");
        final MenuItem item2 = new MenuItem("Brake less");
        item1.setEnabled(braking.getValue() + 1 < hitpoints && brakingMap.containsKey(braking.getValue() + 1));
        item2.setEnabled(braking.getValue() > finalMinBraking);
        item1.setShortcut(new MenuShortcut(KeyEvent.VK_DOWN));
        item2.setShortcut(new MenuShortcut(KeyEvent.VK_UP));
        item1.addActionListener(e -> {
            final int b = braking.getValue();
            braking.setValue(b + 1);
            game.highlightNodes(brakingMap.get(b + 1));
            item1.setEnabled(braking.getValue() + 1 < hitpoints && brakingMap.containsKey(braking.getValue() + 1));
            item2.setEnabled(braking.getValue() > finalMinBraking);
        });
        item2.addActionListener(e -> {
            final int b = braking.getValue();
            braking.setValue(b - 1);
            game.highlightNodes(brakingMap.get(b - 1));
            item1.setEnabled(braking.getValue() + 1 < hitpoints && brakingMap.containsKey(braking.getValue() + 1));
            item2.setEnabled(braking.getValue() > finalMinBraking);
        });
        game.actionMenu.add(item1);
        game.actionMenu.add(item2);
        final KeyListener keyListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                final char c = e.getKeyChar();
                final int b = braking.getValue();
                if (c == '.' && b > finalMinBraking) {
                    braking.setValue(b - 1);
                    game.highlightNodes(brakingMap.get(b - 1));
                } else if (c == ',' && b + 1 < hitpoints && brakingMap.containsKey(b + 1)) {
                    braking.setValue(b + 1);
                    game.highlightNodes(brakingMap.get(b + 1));
                }
            }
        };
        frame.addKeyListener(keyListener);
        final MutableInt selectedIndex = new MutableInt(-1);
        final MouseAdapter mouseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final Map<Integer, Integer> nodeToIndex = indexMap.get(braking.getValue());
                if (nodeToIndex != null) {
                    final Integer nodeId = game.getNodeId(e.getX(), e.getY());
                    if (nodeId != null) {
                        final Integer index = nodeToIndex.get(nodeId);
                        if (index != null) {
                            selectedIndex.setValue(index);
                        }
                    }
                }
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                final Integer nodeId = game.getNodeId(e.getX(), e.getY());
                game.setMouseOverHighlightNodeIndex(nodeId == null ? -1 : nodeId);
            }
        };
        game.addMouseListener(mouseListener);
        game.addMouseMotionListener(mouseListener);
        while (!interrupted) {
            try {
                Thread.sleep(listenerDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final int index = selectedIndex.getValue();
            if (index != -1) {
                game.highlightNodes(null);
                game.setMouseOverHighlightNodeIndex(-1);
                frame.removeKeyListener(keyListener);
                game.removeMouseListener(mouseListener);
                game.removeMouseMotionListener(mouseListener);
                game.actionMenu.removeAll();
                return new SelectedIndex().index(index);
            }
        }
        return new SelectedIndex().index(0);
    }

    @Override
    public void notify(Object notification) {
        if (notification instanceof FinalStandings) {
            final FinalStandings standings = (FinalStandings) notification;
            if (!initialStandingsReceived) {
                profile.standingsReceived(standings.getStats(), trackId);
                profile.getManager().saveProfiles();
                initialStandingsReceived = true;
            } else {
                profile.standingsReceived(standings.getStats(), null);
                profile.getManager().saveProfiles();
            }
        }
    }
}
