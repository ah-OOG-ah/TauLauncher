package org.taumc.launcher.gui.screens.mods;

import org.taumc.launcher.core.mods.ModUpdate;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class ModUpdateDialog {
    public static List<ModUpdate> showModUpdateDialog(Component parent, List<ModUpdate> updates) {
        // Checkbox for each update
        Map<ModUpdate, JCheckBox> checkBoxes = new LinkedHashMap<>();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Select updates to apply:"));
        panel.add(Box.createVerticalStrut(10));

        for (ModUpdate update : updates) {
            String label = String.format("%s → %s",
                    update.originalFile().getFileName(),
                    update.update().fileName());
            JCheckBox checkBox = new JCheckBox(label, true);
            checkBoxes.put(update, checkBox);
            panel.add(checkBox);
        }

        int result = JOptionPane.showConfirmDialog(parent, panel, "Mod Updates",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return checkBoxes.entrySet().stream()
                    .filter(e -> e.getValue().isSelected())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList(); // User cancelled
        }
    }
}
