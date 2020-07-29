package gp;

import javax.swing.*;
import java.awt.*;

class UIUtil {

    static int getX(MapEditor.Corner corner, Dimension dim, int w) {
        switch (corner) {
            case NE: return dim.width - w;
            case SE: return dim.width - w;
            case SW: return 0;
            case NW: return 0;
        }
        return 0;
    }

    static int getY(MapEditor.Corner corner, Dimension dim, int h) {
        switch (corner) {
            case NE: return 0;
            case SE: return dim.height - h;
            case SW: return dim.height - h;
            case NW: return 0;
        }
        return 0;
    }

    static void drawInfoBox(Graphics2D g2d, Dimension dim, int playerCount, MapEditor.Corner corner) {
        final int w = 250;
        final int h = 5 + 15 * playerCount;
        final int x = getX(corner, dim, w);
        final int y = getY(corner, dim, h);
        g2d.setColor(Color.GRAY);
        g2d.fillRect(x, y, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(x, y, w, h);
    }

    static void drawTurnMarker(Graphics2D g2d, Dimension dim, int playerCount, MapEditor.Corner corner, int i) {
        final int w = 250;
        final int h = 5 + 15 * playerCount;
        final int y = getY(corner, dim, h);
        final int iy = y + 5 + 15 * i;
        int[] px;
        int[] py;
        switch (corner) {
            case NE:
            case SE:
                final int x = getX(corner, dim, w);
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
