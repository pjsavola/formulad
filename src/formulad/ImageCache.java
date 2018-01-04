package formulad;

import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public abstract class ImageCache {
	private static Map<String, BufferedImage> imageCache = new HashMap<>();

	public static BufferedImage getImage(String path) {
		BufferedImage image = imageCache.get(path);
		if (image != null) {
			return image;
		}
		try {
			image = ImageIO.read(new File(path));
		} catch (IOException e) {
			throw new RuntimeException("Image " + path + " is missing");
		}
		return updateCache(image, path, imageCache);
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