package gp;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.swing.*;

public abstract class ImageCache {
    private static Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();

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
        //DataBuffer buff = image.getRaster().getDataBuffer();
        //System.err.println("Bytes cached " + key + ": " + buff.getSize() * DataBuffer.getDataTypeSize(buff.getDataType()) / 8);
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

    public static List<BufferedImage> getCarIcons() {
        final List<BufferedImage> icons = new ArrayList<>();
        GraphicsConfiguration gc = getConfiguration();
        int[] sizes = { 16, 32, 64, 128 };
        for (int size : sizes) {
            BufferedImage compatibleImage = gc.createCompatibleImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics g = compatibleImage.getGraphics();
            Player.draw((Graphics2D) g, size / 2, size / 2, 0, new Color(Main.defaultColor1), new Color(Main.defaultColor2), 1.0);
            g.dispose();
            icons.add(compatibleImage);
        }
        return icons;
    }
}
