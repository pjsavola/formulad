package gp;

import javax.swing.*;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WindowChanger extends WindowAdapter {
    private final JFrame frame;
    private final JPanel mainMenu;
    private JPanel panel;
    private Lobby lobby;
    private Game game;
    private String name;
    private boolean confirm;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    WindowChanger(JFrame frame, JPanel mainMenu) {
        this.frame = frame;
        this.mainMenu = mainMenu;
    }

    void reset(JPanel panel) {
        this.panel = panel == mainMenu ? null : panel;
        this.lobby = null;
        this.game = null;
        this.name = null;
        this.confirm = false;
    }

    void contentChanged(JPanel panel, Lobby lobby, Game game, String name, boolean confirm) {
        this.panel = panel;
        this.lobby = lobby;
        this.game = game;
        this.name = name;
        this.confirm = confirm;
        final Runnable task = frame::repaint;
        scheduler.schedule(task, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void windowClosing(WindowEvent e) {
        super.windowClosing(e);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        final int confirmed = confirm ? JOptionPane.showConfirmDialog(panel,
                "Are you sure you want to terminate the " + name + "?", "Confirm",
                JOptionPane.YES_NO_OPTION) : JOptionPane.YES_OPTION;
        if (confirmed == JOptionPane.YES_OPTION) {
            for (KeyListener listener : frame.getKeyListeners()) {
                frame.removeKeyListener(listener);
            }
            if (lobby != null) {
                lobby.close();
            }
            if (game != null) {
                game.exit();
            } else if (panel != null) {
                frame.setMenuBar(new MainMenuBar(frame, mainMenu));
                frame.setContentPane(mainMenu);
                frame.pack();
                reset(null);
            } else {
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            }
        }
    }
}
