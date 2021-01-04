package gp;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Random;

public class ProfileStats extends JDialog {

    ProfileStats(JFrame frame, Profile profile) {
        super(frame);
        setTitle(profile.getName() + " stats");
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        final JPanel contents = new JPanel();
        // Filter by single/champ
        // Filter by track
        // starts
        // completed
        // dnf
        // aborted
        // laps completed
        // time used
        // among COMPLETED:
        // wins
        // podiums
        // points

        setContentPane(contents);
        pack();
        setModal(true);
        setLocationRelativeTo(frame);
        setVisible(true);
    }
}
