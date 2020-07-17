package formulad;

import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

class TrackPreviewButton extends JButton {
    private final JPanel panel;
    private final Lobby lobby;
    private String trackId;

    TrackPreviewButton(JFrame frame, JPanel panel, Lobby lobby) {
        this.panel = panel;
        this.lobby = lobby;
        addActionListener(e -> {
            final List<String> filenames = new ArrayList<>();
            try (InputStream in = FormulaD.class.getResourceAsStream("/"); BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                String resource;
                while ((resource = br.readLine()) != null) {
                    filenames.add(resource);
                }
            } catch (IOException ex) {
                FormulaD.log.log(Level.SEVERE, "Unable to read resource directory", e);
                return;
            }
            final JPanel trackPanel = new JPanel(new GridLayout(0, 2));
            final JDialog trackDialog = new JDialog(frame);
            filenames.stream().filter(name -> name.endsWith(".dat")).filter(FormulaD::validateTrack).forEach(f -> {
                final BufferedImage image = getImage(f);
                if (image == null) {
                    return;
                }
                final JButton selectTrackButton = new JButton();
                final ImageIcon icon = createIcon(image);
                selectTrackButton.addActionListener(l -> {
                    setIcon(icon);
                    setTrack(f);
                    trackDialog.setVisible(false);
                });
                selectTrackButton.setIcon(icon);
                trackPanel.add(selectTrackButton);
            });
            trackDialog.setTitle("Select track");
            trackDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            trackDialog.setContentPane(trackPanel);
            trackDialog.pack();
            trackDialog.setModal(true);
            trackDialog.setLocationRelativeTo(frame);
            trackDialog.setVisible(true);
        });
    }

    boolean setTrack(String trackId) {
        final BufferedImage image = getImage(trackId);
        if (image == null) {
            return false;
        }
        setIcon(createIcon(image));
        if (lobby != null && !trackId.equals(this.trackId)) {
            lobby.setTrack(trackId);
        }
        this.trackId = trackId;
        return true;
    }

    String getTrack() {
        return trackId;
    }

    private BufferedImage getImage(String trackId) {
        final Pair<String, MapEditor.Corner> header = MapEditor.loadHeader(trackId);
        if (header == null) {
            JOptionPane.showConfirmDialog(panel, "Invalid track " + trackId, "Error", JOptionPane.DEFAULT_OPTION);
        } else {
            return ImageCache.getImage("/" + header.getLeft());
        }
        return null;
    }

    private static ImageIcon createIcon(BufferedImage image) {
        final ImageIcon icon = new ImageIcon();
        final int x = image.getWidth();
        final int y = image.getHeight();
        final int scale = Math.max(x / 300, y / 300);
        icon.setImage(image.getScaledInstance(x / scale, y / scale, Image.SCALE_FAST));
        return icon;
    }
}
