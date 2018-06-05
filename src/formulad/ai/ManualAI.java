package formulad.ai;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;

import formulad.Game;
import sun.java2d.xr.MutableInteger;

public class ManualAI implements AI {

    private int playerId;
    private Map<Integer, Node> nodeMap;
    private Map<Integer, PlayerData> playerMap;
    private PlayerData player;
    private final AI ai;
    private final JFrame frame;
    private final Game game;
    private boolean automaticMove;

    public ManualAI(AI ai, JFrame frame, Game game) {
        this.ai = ai;
        this.frame = frame;
        this.game = game;
    }

    @Override
    public String getName() {
        return ai.getName();
    }

    @Override
    public boolean isActive() {
        return ai.isActive();
    }

    @Override
    public void initialize(int playerId, int startNodeId, int[][] nodes, int[][] edges) {
        this.playerId = playerId;
        nodeMap = AIUtil.readNodeMap(nodes, edges);
        ai.initialize(playerId, startNodeId, nodes, edges);
    }

    @Override
    public int selectGear(int[][] players) {
        playerMap = AIUtil.readPlayerMap(players, nodeMap);
        player = playerMap.get(playerId);
        if (player == null) {
            throw new RuntimeException("No data sent for player: " + playerId);
        }
        final MutableInteger selectedGear = new MutableInteger(0);
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
                    automaticMove = true;
                    selectedGear.setValue(ai.selectGear(players));
                }
                else if (c >= '1' && c <= '6') {
                    if (AIUtil.validateGear(player, c - '0')) {
                        selectedGear.setValue(c - '0');
                    }
                }
            }
        };
        frame.addKeyListener(keyListener);
        while (true) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final int newGear = selectedGear.getValue();
            if (newGear != 0) {
                frame.removeKeyListener(keyListener);
                return newGear;
            }
        }
    }

    @Override
    public int selectTarget(int[][] targets) {
        if (automaticMove) {
            automaticMove = false;
            return ai.selectTarget(targets);
        }
        final Map<Integer, Map<Integer, Integer>> brakingMap = new HashMap<>();
        final Map<Integer, Map<Integer, Integer>> indexMap = new HashMap<>();
        for (int i = 0; i < targets.length; i++) {
            final int[] target = targets[i];
            final int nodeId = target[0];
            final int overshootDamage = target[1];
            final int brakingDamage = target[2];
            brakingMap.computeIfAbsent(brakingDamage, _key -> new HashMap<>()).put(nodeId, brakingDamage + overshootDamage);
            indexMap.computeIfAbsent(brakingDamage, _key -> new HashMap<>()).put(nodeId, i);
        }
        game.highlightNodes(brakingMap.get(0));
        final MutableInteger braking = new MutableInteger(0);
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
                if (c == '.' && b > 0) {
                    braking.setValue(b - 1);
                    game.highlightNodes(brakingMap.get(b - 1));
                } else if (c == ',' && b + 1 < player.getHitpoints() && brakingMap.containsKey(b + 1)) {
                    braking.setValue(b + 1);
                    game.highlightNodes(brakingMap.get(b + 1));
                }
            }
        };
        frame.addKeyListener(keyListener);
        final MutableInteger selectedIndex = new MutableInteger(-1);
        final MouseListener mouseListener = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final Map<Integer, Integer> nodeToIndex = indexMap.get(braking.getValue());
                if (nodeToIndex != null) {
                    final int nodeId = game.getNodeId(e.getX(), e.getY());
                    final Integer index = nodeToIndex.get(nodeId);
                    if (index != null) {
                        selectedIndex.setValue(index);
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
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final int index = selectedIndex.getValue();
            if (index != -1) {
                game.highlightNodes(null);
                frame.removeKeyListener(keyListener);
                game.removeMouseListener(mouseListener);
                return index;
            }
        }
    }

    @Override
    public void sendGameOver() {
    }
}
