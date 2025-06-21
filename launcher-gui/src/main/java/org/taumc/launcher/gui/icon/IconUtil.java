package org.taumc.launcher.gui.icon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IconUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(IconUtil.class);
    private static final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();

    public static ImageIcon loadIconFromURL(String urlStr, int size) {
        if (urlStr == null) {
            return null;
        }

        // Return from cache if available
        String cacheKey = urlStr + "#" + size;
        if (iconCache.containsKey(cacheKey)) {
            return iconCache.get(cacheKey);
        }

        try {
            URL url = new URL(urlStr);
            Image image = ImageIO.read(url);
            if (image == null) {
                LOGGER.error("Failed to read image from {}", urlStr);
                return null;
            }

            Image scaled = image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(scaled);
            iconCache.put(cacheKey, icon);
            return icon;
        } catch (IOException e) {
            LOGGER.error("Failed to read image", e);
            return null;
        }
    }
}