package gp;

import javax.swing.*;
import java.awt.*;

class UIUtil {

    static int getX(MapEditor.Corner corner, JPanel panel, int w) {
        switch (corner) {
            case NE: return panel.getWidth() - w;
            case SE: return panel.getWidth() - w;
            case SW: return 0;
            case NW: return 0;
        }
        return 0;
    }

    static int getY(MapEditor.Corner corner, JPanel panel, int h) {
        switch (corner) {
            case NE: return 0;
            case SE: return panel.getHeight() - h;
            case SW: return panel.getHeight() - h;
            case NW: return 0;
        }
        return 0;
    }

    static void drawInfoBox(Graphics2D g2d, JPanel panel, int playerCount, MapEditor.Corner corner) {
        final int w = 250;
        final int h = 5 + 15 * playerCount;
        final int x = getX(corner, panel, w);
        final int y = getY(corner, panel, h);
        g2d.setColor(Color.GRAY);
        g2d.fillRect(x, y, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(x, y, w, h);
    }

    static void drawTurnMarker(Graphics2D g2d, JPanel panel, int playerCount, MapEditor.Corner corner, int i) {
        final int w = 250;
        final int h = 5 + 15 * playerCount;
        final int y = getY(corner, panel, h);
        final int iy = y + 5 + 15 * i;
        int[] px;
        int[] py;
        switch (corner) {
            case NE:
            case SE:
                final int x = getX(corner, panel, w);
                px = new int[] { x - 2, x - 7, x - 7 };
                py = new int[] { iy + 5, iy + 2, iy + 8 };
                break;
            case SW:
            case NW:
                px = new int[] { w + 2, w + 7, w + 7 };
                py = new int[] { iy + 5, iy + 2, iy + 8 };
                break;
            default:
                return;
        }
        g2d.setColor(Color.RED);
        g2d.fillPolygon(px, py, 3);
    }
}
