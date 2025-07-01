package org.taumc.launcher.gui.icon;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class IconRegistry {
    public static final int ICON_HEIGHT = 64;

    private record IconKey(String name, Path instance) {}
    private static final Map<IconKey, ImageIcon> CACHE = new HashMap<>();

    public static void resetCache() {
        CACHE.values().forEach(i -> i.getImage().flush());
        CACHE.clear();
    }

    public static ImageIcon loadAndScaleImageIcon(InputStream is, int fixedHeight) {
        try {
            BufferedImage img = ImageIO.read(is);
            if (img == null) throw new IllegalArgumentException("Unsupported image format");

            if (img.getHeight() == fixedHeight) {
                return new ImageIcon(img);
            }

            int originalWidth = img.getWidth();
            int originalHeight = img.getHeight();
            double scale = (double) fixedHeight / originalHeight;
            int scaledWidth = (int) (originalWidth * scale);

            Image scaled = img.getScaledInstance(scaledWidth, fixedHeight, Image.SCALE_SMOOTH);
            img.flush();
            return new ImageIcon(scaled);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load image", e);
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static ImageIcon loadIcon(String iconName, Path instance) {
        var path = instance.resolve(iconName + ".png");
        try {
            return loadAndScaleImageIcon(Files.newInputStream(path), ICON_HEIGHT);
        } catch (Exception e) {
            var iconStream = IconRegistry.class.getResourceAsStream("/taulauncher/icons/" + iconName + ".png");
            if (iconStream != null) {
                return loadAndScaleImageIcon(iconStream, ICON_HEIGHT);
            } else {
                return loadAndScaleImageIcon(IconRegistry.class.getResourceAsStream("/taulauncher/icons/default_instance.png"), ICON_HEIGHT);
            }
        }
    }

    public static ImageIcon findIcon(String iconName, Path instance) {
        return CACHE.computeIfAbsent(new IconKey(iconName, instance), k -> loadIcon(k.name, k.instance));
    }
}
