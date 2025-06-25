package org.taumc.launcher.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class SwingHelpers {
    /**
     * Adds a separator line to the given container.
     *
     * @param container The JPanel or container to add the separator to.
     * @param orientation SwingConstants.HORIZONTAL or SwingConstants.VERTICAL
     * @param thickness thickness in pixels (e.g., 1 or 2)
     */
    public static void addSeparator(JComponent container, int orientation, int thickness, int padding) {
        JSeparator separator = new JSeparator(orientation);
        if (orientation == SwingConstants.HORIZONTAL) {
            separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, thickness));
        } else if (orientation == SwingConstants.VERTICAL) {
            separator.setMaximumSize(new Dimension(thickness, Integer.MAX_VALUE));
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(new EmptyBorder(padding, padding, padding, padding));
        wrapper.add(separator, BorderLayout.CENTER);

        // Limit wrapper's maximum size to avoid stretching
        if (orientation == SwingConstants.HORIZONTAL) {
            wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, thickness + 2 * padding));
        } else {
            wrapper.setMaximumSize(new Dimension(thickness + 2 * padding, Integer.MAX_VALUE));
        }

        container.add(wrapper);
    }

    public static void addSeparator(JComponent container, int orientation) {
        addSeparator(container, orientation, 5, 0);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> immutableListOf(ListModel<T> listModel) {
        Object[] elements = new Object[listModel.getSize()];
        for (int i = 0; i < elements.length; i++) {
            elements[i] = listModel.getElementAt(i);
        }
        return (List<T>)List.of(elements);
    }

    public static void showFileInFolder(File file) {
        if (!file.exists()) {
            System.err.println("File does not exist: " + file);
            return;
        }

        try {
            // Try platform-specific methods
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows: explorer /select,"C:\path\to\file.txt"
                new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();

            } else if (os.contains("mac")) {
                // macOS: open -R /path/to/file
                new ProcessBuilder("open", "-R", file.getAbsolutePath()).start();

            } else if (os.contains("nux") || os.contains("nix")) {
                // Linux: best effort
                File parent = file.getParentFile();
                if (parent != null && parent.exists()) {
                    new ProcessBuilder("xdg-open", parent.getAbsolutePath()).start();
                }
            } else {
                // Fallback
                Desktop.getDesktop().open(file.getParentFile());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isAnyWindowFocused() {
        for (Window window : Window.getWindows()) {
            if (window.isVisible() && window.isFocused()) {
                return true;
            }
        }
        return false;
    }

    public static String ellipsize(FontMetrics fm, String text, int maxWidth) {
        String ellipsis = "…";

        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }

        int low = 0;
        int high = text.length();

        while (low < high) {
            int mid = (low + high) / 2;
            String candidate = text.substring(0, mid) + ellipsis;
            int width = fm.stringWidth(candidate);

            if (width <= maxWidth) {
                low = mid + 1; // try longer substring
            } else {
                high = mid;    // try shorter substring
            }
        }

        // low is now the first index that *doesn't* fit, so use low-1
        int cutOff = Math.max(low - 1, 0);
        return text.substring(0, cutOff) + ellipsis;
    }
}