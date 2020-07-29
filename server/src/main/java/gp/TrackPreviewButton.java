package gp;

import gp.ai.TrackData;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.security.CodeSource;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class TrackPreviewButton extends JButton implements TrackSelector {
    private final JPanel panel;
    private final Lobby lobby;
    private TrackData data;

    public static class ResourceWalker {
        public static void main(String[] args) throws URISyntaxException, IOException {
            URI uri = ResourceWalker.class.getResource("/resources").toURI();
            Path myPath;
            if (uri.getScheme().equals("jar")) {
                FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
                myPath = fileSystem.getPath("/resources");
            } else {
                myPath = Paths.get(uri);
            }
            Stream<Path> walk = Files.walk(myPath, 1);
            for (Iterator<Path> it = walk.iterator(); it.hasNext();){
                System.out.println(it.next());
            }
        }
    }

    TrackPreviewButton(JFrame frame, JPanel panel, Lobby lobby) {
        this.panel = panel;
        this.lobby = lobby;
        addActionListener(e -> {
            openTrackSelectionDialog(frame, this);
        });
    }

    static void openTrackSelectionDialog(Container parent, TrackSelector trackSelector) {
        final List<String> internal = new ArrayList<>();
        final List<String> external = new ArrayList<>();
        getAllTracks(internal, external);
        final JPanel trackPanel = new JPanel(new GridLayout(0, 2));
        final JDialog trackDialog = parent instanceof JFrame ? new JDialog((JFrame) parent) : new JDialog((JDialog) parent, true);
        internal.stream().map(f -> TrackData.createTrackData(f, false)).filter(Objects::nonNull).forEach(data -> {
            final JButton selectTrackButton = new JButton();
            final ImageIcon icon = createIcon(data.getBackgroundImage());
            selectTrackButton.addActionListener(l -> {
                trackSelector.setTrack(data, icon);
                trackDialog.setVisible(false);
            });
            selectTrackButton.setIcon(icon);
            trackPanel.add(selectTrackButton);
        });
        external.stream().map(f -> TrackData.createTrackData(f, true)).filter(Objects::nonNull).forEach(data -> {
            final JButton selectTrackButton = new JButton();
            final ImageIcon icon = createIcon(data.getBackgroundImage());
            selectTrackButton.addActionListener(l -> {
                trackSelector.setTrack(data, icon);
                trackDialog.setVisible(false);
            });
            selectTrackButton.setIcon(icon);
            trackPanel.add(selectTrackButton);
        });
        final JScrollPane scrollPane = new JScrollPane(trackPanel);
        trackDialog.setTitle("Select track");
        trackDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        trackDialog.setContentPane(scrollPane);
        trackDialog.pack();
        trackDialog.setModal(true);
        trackDialog.setLocationRelativeTo(parent);
        trackDialog.setVisible(true);
    }

    public static void getAllTracks(List<String> internal, List<String> external) {
        final boolean ide = true;
        if (ide) {
            try (InputStream in = Main.class.getResourceAsStream("/"); BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                String resource;
                while ((resource = br.readLine()) != null) {
                    if (resource.toLowerCase().endsWith(".dat")) {
                        internal.add(resource);
                    }
                }
            } catch (IOException ex) {
                Main.log.log(Level.SEVERE, "Unable to read resource directory", ex);
                return;
            }
        } else {
            try {
                final CodeSource src = Main.class.getProtectionDomain().getCodeSource();
                if (src != null) {
                    URL jar = src.getLocation();
                    ZipInputStream zip = new ZipInputStream(jar.openStream());
                    while(true) {
                        ZipEntry z = zip.getNextEntry();
                        if (z == null) {
                            break;
                        }
                        final String name = z.getName();
                        if (name.toLowerCase().endsWith(".dat")) {
                            internal.add(name);
                        }
                    }
                }
                else {
                    Main.log.log(Level.SEVERE, "Unable to read resource directory");
                    return;
                }
            } catch (IOException ex) {
                Main.log.log(Level.SEVERE, "Unable to read resource directory", ex);
                return;
            }
        }
        internal.sort(String::compareTo);

        // Try to search for custom tracks
        final File file = new File(".");
        final File[] dataFiles = file.listFiles(f -> f.getName().toLowerCase().endsWith(".dat"));
        if (dataFiles != null) {
            for (File f : dataFiles) {
                if (!internal.contains(f.getName())) {
                    external.add(f.getName());
                }
            }
        }
        external.sort(String::compareTo);
    }

    @Override
    public void setTrack(TrackData newData, ImageIcon icon) {
        if (newData == null) {
            return;
        }
        if (!newData.equals(data)) {
            data = newData;
            setIcon(icon);
            if (lobby != null) {
                lobby.setTrack(newData);
            }
        }
    }

    TrackData getTrackData() {
        return data;
    }

    static ImageIcon createIcon(BufferedImage image) {
        final ImageIcon icon = new ImageIcon();
        final int x = image.getWidth();
        final int y = image.getHeight();
        final double scaleX = x / 400.0;
        final double scaleY = y / 300.0;
        icon.setImage(image.getScaledInstance((int) (x / scaleX), (int) (y / scaleY), Image.SCALE_FAST));
        return icon;
    }
}
