package org.taumc.launcher.gui.minecraft;

import java.awt.*;
import java.awt.image.BufferedImage;

public class SkinHelper {
    public static BufferedImage extractHead(BufferedImage skin) {
        int faceSize = 8;

        // Crop face (8x8 at 8,8)
        BufferedImage face = skin.getSubimage(8, 8, faceSize, faceSize);

        // Crop hat/overlay (8x8 at 40,8)
        BufferedImage hat = skin.getSubimage(40, 8, faceSize, faceSize);

        // Create new image with transparency
        BufferedImage head = new BufferedImage(faceSize, faceSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = head.createGraphics();

        // Draw face first
        g.drawImage(face, 0, 0, null);

        // Draw hat on top (may have transparency)
        g.drawImage(hat, 0, 0, null);

        g.dispose();
        return head;
    }
}
