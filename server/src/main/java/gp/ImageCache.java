package gp;

import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public abstract class ImageCache {
    private static Map<String, BufferedImage> imageCache = new HashMap<>();

    public static BufferedImage getImage(String name) {
        BufferedImage image = imageCache.get(name);
        if (image != null) {
            return image;
        }
        try (InputStream is = ImageCache.class.getResourceAsStream(name)) {
            image = ImageIO.read(is);
        } catch (IOException e) {
            throw new RuntimeException("Image " + name + " is missing");
        }
        return updateCache(image, name, imageCache);
    }

    public static BufferedImage getImageFromPath(String absolutePath) {
        BufferedImage image = imageCache.get(absolutePath);
        if (image != null) {
            return image;
        }
        try (InputStream is = new FileInputStream(absolutePath)) {
            image = ImageIO.read(is);
        } catch (IOException e) {
            throw new RuntimeException("Image " + absolutePath + " is missing");
        }
        return updateCache(image, absolutePath, imageCache);
    }

    private static <T> BufferedImage updateCache(BufferedImage image, T key, Map<T, BufferedImage> cache) {
        image = toCompatibleImage(image);
        cache.put(key, image);
        return image;
    }

    private static BufferedImage toCompatibleImage(BufferedImage image) {
        GraphicsConfiguration gc = getConfiguration();
        if (image.getColorModel().equals(gc.getColorModel())) {
            return image;
        }
        BufferedImage compatibleImage = gc.createCompatibleImage(
                image.getWidth(), image.getHeight(),
                image.getTransparency());
        Graphics g = compatibleImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return compatibleImage;
    }

    private static GraphicsConfiguration getConfiguration() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().
                getDefaultScreenDevice().getDefaultConfiguration();
    }
}
