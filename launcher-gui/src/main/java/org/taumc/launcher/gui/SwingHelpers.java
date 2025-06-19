package org.taumc.launcher.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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
}