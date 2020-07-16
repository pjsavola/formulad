package formulad.ai;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;

import formulad.Profile;
import formulad.Game;
import formulad.model.*;
import formulad.model.Gear;
import org.apache.commons.lang3.mutable.MutableInt;

public class ManualAI implements AI {

    private String playerId;
    private int hitpoints;
    private int gear;
    private final AI ai;
    private final JFrame frame;
    private final Game game;
    private boolean automaticMove;
    private static final int listenerDelay = 50;
    private final Profile profile;
    private boolean initialStandingsReceived;

    public ManualAI(AI ai, JFrame frame, Game game, Profile profile) {
        this.ai = ai;
        this.frame = frame;
        this.game = game;
        this.profile = profile;
    }

    @Override
    public NameAtStart startGame(Track track) {
        this.playerId = track.getPlayer().getPlayerId();
        return ai.startGame(track).name(profile.getName()).id(profile.getId());
    }

    @Override
    public Gear selectGear(GameState gameState) {
        for (PlayerState playerState : gameState.getPlayers()) {
            if (playerId.equals(playerState.getPlayerId())) {
                hitpoints = playerState.getHitpoints();
                gear = playerState.getGear();
                break;
            }
        }
        if (hitpoints == 0) {
            throw new RuntimeException("No data sent for player: " + playerId);
        }
        final MutableInt selectedGear = new MutableInt(0);
        final KeyListener keyListener = new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }
            @Override
            public void keyPressed(KeyEvent e) {
            }
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
                    if (AIUtil.validateGear(hitpoints, gear, c - '0')) {
                        selectedGear.setValue(c - '0');
                    }
                }
            }
        };
        frame.addKeyListener(keyListener);
        frame.requestFocus();
        while (true) {
            try {
                Thread.sleep(listenerDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final int newGear = selectedGear.getValue();
            if (newGear != 0) {
                frame.removeKeyListener(keyListener);
                return new Gear().gear(newGear);
            }
        }
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
        final KeyListener keyListener = new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }
            @Override
            public void keyPressed(KeyEvent e) {
            }
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
        final MouseListener mouseListener = new MouseListener() {
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
        };
        game.addMouseListener(mouseListener);
        while (true) {
            try {
                Thread.sleep(listenerDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final int index = selectedIndex.getValue();
            if (index != -1) {
                game.highlightNodes(null);
                frame.removeKeyListener(keyListener);
                game.removeMouseListener(mouseListener);
                return new SelectedIndex().index(index);
            }
        }
    }

    @Override
    public void notify(Object notification) {
        if (notification instanceof FinalStandings) {
            final FinalStandings standings = (FinalStandings) notification;
            if (!initialStandingsReceived) {
                profile.standingsReceived(standings.getStats(), true);
                initialStandingsReceived = true;
            } else {
                profile.standingsReceived(standings.getStats(), false);
            }
        }
    }
}
