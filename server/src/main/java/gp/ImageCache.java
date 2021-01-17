package gp;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public abstract class ImageCache {

    public static BufferedImage getImage(String name) {
        try (InputStream is = ImageCache.class.getResourceAsStream(name)) {
            return toCompatibleImage(ImageIO.read(is));
        } catch (IOException e) {
            throw new RuntimeException("Image " + name + " is missing");
        }
    }

    public static BufferedImage getImageFromPath(String absolutePath) {
        try (InputStream is = new FileInputStream(absolutePath)) {
            return toCompatibleImage(ImageIO.read(is));
        } catch (IOException e) {
            throw new RuntimeException("Image " + absolutePath + " is missing");
        }
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

    static List<BufferedImage> getCarIcons() {
        final List<BufferedImage> icons = new ArrayList<>();
        GraphicsConfiguration gc = getConfiguration();
        int[] sizes = { 16, 32, 64, 128 };
        for (int size : sizes) {
            BufferedImage compatibleImage = gc.createCompatibleImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics g = compatibleImage.getGraphics();
            final int[] colors = { Main.defaultColor1, Main.defaultColor2, Main.defaultColor1, 0x000000 };
            Player.draw((Graphics2D) g, size / 2, size / 2, 0, colors, 1.0);
            g.dispose();
            icons.add(compatibleImage);
        }
        return icons;
    }
}
