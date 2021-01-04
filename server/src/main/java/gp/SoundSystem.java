package gp;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.*;
import java.net.URL;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SoundSystem {
    public enum Type {
        COLLISION("collision.wav"),
        DNF("dnf.wav"),
        ENGINE("engine.wav"),
        GEARS("gears.wav"),
        PITS("pits.wav");

        Type(String name) {
            this.name = name;
        }

        private final String name;
    }

    private static Map<Type, URL> resources;

    public static void playSound(Type type) {
        URL url = getSound(type);
        if (url == null) return;

        try {
            Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(url));
            clip.start();
        } catch (Exception e) {
            // Sound didn't work
        }
    }

    private static URL getSound(Type type) {
        if (resources == null) {
            resources = new HashMap<>();
        }
        final URL soundFile = resources.get(type);
        if (soundFile != null) {
            return soundFile;
        }
        final String fileName = type.name;
        if (Main.ide) {
            try (InputStream in = Main.class.getResourceAsStream("/"); BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                String resource;
                while ((resource = br.readLine()) != null) {
                    if (resource.toLowerCase().equals(fileName)){
                        final URL url = Main.class.getResource("/" + resource);
                        resources.put(type, url);
                        return url;
                    }
                }
            } catch (IOException ex) {
                Main.log.log(Level.SEVERE, "Unable to read resource directory", ex);
                return null;
            }
        } else {
            try {
                final CodeSource src = Main.class.getProtectionDomain().getCodeSource();
                if (src != null) {
                    URL jar = src.getLocation();
                    ZipInputStream zip = new ZipInputStream(jar.openStream());
                    while (true) {
                        ZipEntry z = zip.getNextEntry();
                        if (z == null) {
                            break;
                        }
                        final String name = z.getName();
                        if (name.toLowerCase().equals(fileName)) {
                            final URL url = Main.class.getResource("/" + name);
                            resources.put(type, url);
                            return url;
                        }
                    }
                }
                else {
                    Main.log.log(Level.SEVERE, "Unable to read resource directory");
                    return null;
                }
            } catch (IOException ex) {
                Main.log.log(Level.SEVERE, "Unable to read resource directory", ex);
                return null;
            }
        }
        return null;
    }

}
