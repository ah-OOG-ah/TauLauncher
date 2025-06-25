package org.taumc.launcher.gui.screens.mods;

import org.taumc.launcher.core.mods.ModUpdate;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class ModUpdateDialog {
    public static List<ModUpdate> showModUpdateDialog(Component parent, List<ModUpdate> updates) {
        Map<ModUpdate, JCheckBox> checkBoxes = new LinkedHashMap<>();

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));

        for (ModUpdate update : updates) {
            String label = String.format("%s → %s",
                    update.originalFile().getFileName(),
                    update.update().fileName());
            JCheckBox checkBox = new JCheckBox(label, true);
            checkBoxes.put(update, checkBox);
            checkboxPanel.add(checkBox);
        }

        JScrollPane scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setPreferredSize(new Dimension(400, Math.min(checkboxPanel.getComponentCount() * 30, 300)));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Try 24 or 32 for even faster scroll

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(0, 10));
        panel.add(new JLabel("Select updates to apply:"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Mod Updates",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return checkBoxes.entrySet().stream()
                    .filter(e -> e.getValue().isSelected())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList(); // Cancelled
        }
    }
}
