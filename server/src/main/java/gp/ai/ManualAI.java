package gp.ai;

import gp.Game;
import gp.Profile;
import gp.model.*;
import gp.model.Gear;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ManualAI extends BaseAI {

    private int hitpoints;
    private int lapsToGo;
    private Node location;
    private final AI ai;
    private final JFrame frame;
    private final Game game;
    private boolean automaticMove;
    private static final int listenerDelay = 50;
    private final Profile profile;
    private boolean initialStandingsReceived;

    public volatile boolean interrupted;

    public ManualAI(AI ai, JFrame frame, Game game, Profile profile, TrackData data) {
        super(data);
        this.ai = ai;
        this.frame = frame;
        this.game = game;
        this.profile = profile;
    }

    private String getAidText(int distance) {
        return distance == -1 ? ": N/A" : ": " + distance;
    }

    @Override
    public Gear selectGear(GameState gameState) {
        final Set<Node> blockedNodes = new HashSet<>();
        int stopCount = 0;
        for (PlayerState playerState : gameState.getPlayers()) {
            if (playerId.equals(playerState.getPlayerId())) {
                hitpoints = playerState.getHitpoints();
                lapsToGo = playerState.getLapsToGo();
                gear = playerState.getGear();
                location = data.getNodes().get(playerState.getNodeId());
                stopCount = playerState.getStops();
                tires = playerState.getTires();
                //System.err.println("EVAL: " + evaluate(playerState));
            } else if (playerState.getHitpoints() > 0 && playerState.getLapsToGo() >= 0) {
                blockedNodes.add(data.getNodes().get(playerState.getNodeId()));
            }
        }
        if (hitpoints == 0) {
            throw new RuntimeException("No data sent for player: " + playerId);
        }
        if (ai instanceof ProAI) {
            ((ProAI) ai).updatePlayerInfo(gameState);
        }
        final MutableInt selectedGear = new MutableInt(0);
        final boolean inPits = location != null && location.getType() == NodeType.PIT;
        game.actionMenu.removeAll();
        for (int newGear = 1; newGear <= 6; ++newGear) {
            if (AIUtil.validateGear(hitpoints, gear, newGear, inPits)) {
                final int min = gp.ai.Gear.getMin(newGear);
                final int max = gp.ai.Gear.getMax(newGear);
                String label = "Gear " + newGear + " (" + min + "-" + max + ")";
                if (gear > newGear + 1) {
                    label += " -" + (gear - newGear - 1) + " HP";
                }
                final MenuItem item = new MenuItem(label);
                item.setShortcut(new MenuShortcut(KeyEvent.VK_0 + newGear));
                final int finalNewGear = newGear;
                item.addActionListener(e -> {
                    if (confirmSuspiciousTireChoice()) {
                        selectedGear.setValue(finalNewGear);
                    }
                });
                game.actionMenu.add(item);
            }
        }
        if (tires != null && (gear == 0 || location.hasGarage())) {
            // Query for new tires
            final MenuItem item = new MenuItem("Change tires");
            item.setShortcut(new MenuShortcut(KeyEvent.VK_T));
            item.addActionListener(e -> {
                final String previous = StringUtils.capitalize(tires.getType().name().toLowerCase());
                final String choice = (String) JOptionPane.showInputDialog(frame, "Select tires", "Select tires", JOptionPane.PLAIN_MESSAGE, null, new String[] { "Hard", "Soft", "Wet" }, previous);
                if (choice != null) {
                    tires = new Tires(Tires.Type.valueOf(choice.toUpperCase()));
                    game.getCurrent().setTires(tires); // This is done only to update visuals. Tires will be overwritten by the returned choice later.
                    game.repaint();
                }
            });
            game.actionMenu.add(item);
        }
        if (!location.isPit()) data.getNodes().stream().filter(Node::isPit).forEach(blockedNodes::add);
        final MenuItem item1 = new MenuItem("Min distance to next curve" + getAidText(AIUtil.getMinDistanceToNextCurve(location, blockedNodes)));
        final MenuItem item2 = new MenuItem("Max distance without taking damage" + getAidText(AIUtil.getMaxDistanceWithoutDamage(location, stopCount, blockedNodes)));
        game.drivingAids.add(item1);
        game.drivingAids.add(item2);
        final KeyListener keyListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                final char c = e.getKeyChar();
                if (c == 'a') {
                    final Gear gear = ai.selectGear(gameState);
                    if (gear != null) {
                        automaticMove = true;
                        selectedGear.setValue(gear.getGear());
                        tires = gear.getTires();
                    }
                } else if (c == 'g') {
                    final Gear gear = ai.selectGear(gameState);
                    if (gear != null) {
                        selectedGear.setValue(gear.getGear());
                        tires = gear.getTires();
                    }
                } else if (c >= '1' && c <= '6') {
                    if (AIUtil.validateGear(hitpoints, gear, c - '0', inPits)) {
                        if (confirmSuspiciousTireChoice()) {
                            selectedGear.setValue(c - '0');
                        }
                    }
                } else if (c == 'd') {
                    game.debug = !game.debug;
                    game.repaint();
                }/* else {
                    switch (c) {
                        case 'A':
                            System.out.println("Stops required in next curve: " + AIUtil.getStopsRequiredInNextCurve(location));
                            break;
                        case 'B':
                            System.out.println("Max distance to straight: " + AIUtil.findMaxDistanceToStraight(location));
                            break;
                        case 'C':
                            System.out.println("Max distance to next straight: " + AIUtil.getMaxDistanceToNextStraight(location));
                            break;
                        case 'D':
                            System.out.println("Max distance to straight after next curve: " + AIUtil.getMaxDistanceToStraightAfterNextCurve(location));
                            break;
                        case 'E':
                            System.out.println("Max distances to next area start: " + AIUtil.findMaxDistancesToNextAreaStart(location).values());
                            break;
                        case 'F':
                            System.out.println("Min distances to next area start: " + AIUtil.findMinDistancesToNextAreaStart(location, false).values());
                            System.out.println("Min distances to next area start (allow non-optimal last): " + AIUtil.findMinDistancesToNextAreaStart(location, true).values());
                            break;
                        case 'G':
                            System.out.println("Min distance to pits: " + AIUtil.getMinDistanceToPits(location, blockedNodes));
                            break;
                        case 'H':
                            ai.selectGear(gameState);
                            break;
                    }
                }*/
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
                gear = newGear;
                frame.removeKeyListener(keyListener);
                game.actionMenu.removeAll();
                game.drivingAids.removeAll();
                return new Gear().gear(newGear).tires(tires);
            }
        }
        return new Gear().gear(gear).tires(tires);
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
                } else if (c == 'D') {
                    final Map<Integer, Integer> debugMap = new HashMap<>(   );
                    if (ai instanceof ProAI) {
                        brakingMap.get(b).forEach((nodeId, damage) -> {
                            final ProAI proAI = (ProAI) ai;
                            proAI.debug2 = true;
                            final int score = proAI.evaluate(nodes.get(nodeId), damage, gear);
                            proAI.debug2 = false;
                            debugMap.put(nodeId, score);
                        });
                    }
                    game.highlightNodes(debugMap);
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
        super.notify(notification);
        if (notification instanceof FinalStandings) {
            final FinalStandings standings = (FinalStandings) notification;
            if (!initialStandingsReceived) {
                profile.standingsReceived(standings.getStats(), data.getTrackId(), standings.isSingleRace(), weatherForecast != null);
                profile.getManager().saveProfiles();
                initialStandingsReceived = true;
            } else {
                profile.standingsReceived(standings.getStats(), null, standings.isSingleRace(), weatherForecast != null);
                profile.getManager().saveProfiles();
            }
        }
        ai.notify(notification);
    }

    private boolean confirmSuspiciousTireChoice() {
        if (tires != null && (gear == 0 || location.hasGarage())) {
            if (weatherForecast != null) {
                int rainyTurns = 0;
                for (int i = 5; i < 24; ++i) {
                    if (getWeather(i) == Weather.RAIN) {
                        ++rainyTurns;
                    }
                }
                if (rainyTurns > 10 && tires.getType() != Tires.Type.WET) {
                    final int confirmed = JOptionPane.showConfirmDialog(game,
                            "It seems rainy and you haven't selected wet tires. Are you sure?", "Confirm",
                            JOptionPane.YES_NO_OPTION);
                    return confirmed == JOptionPane.YES_OPTION;
                }
                if (rainyTurns < 5 && tires.getType() == Tires.Type.WET) {
                    final int confirmed = JOptionPane.showConfirmDialog(game,
                            "It seems dry and you have selected wet tires. Are you sure?", "Confirm",
                            JOptionPane.YES_NO_OPTION);
                    return confirmed == JOptionPane.YES_OPTION;
                }
            }
            if (tires.getType() == Tires.Type.HARD && lapsToGo <= 1) {
                final int confirmed = JOptionPane.showConfirmDialog(game,
                        "There is only one lap remaining and you haven't selected soft tires. Are you sure?", "Confirm",
                        JOptionPane.YES_NO_OPTION);
                return confirmed == JOptionPane.YES_OPTION;
            }
        }
        return true;
    }
}
