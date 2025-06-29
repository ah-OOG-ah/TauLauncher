package org.taumc.launcher.gui.screens.mods;

import org.taumc.launcher.core.mods.ModUpdate;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.*;

public class ModUpdateDialog {
    public static List<ModUpdate> showModUpdateDialog(Component parent, List<ModUpdate> updates) {
        // Table model
        DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"", "From", "To"}, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0; // Only the checkbox is editable
            }
        };

        // Map row to update
        List<ModUpdate> rowToUpdate = new ArrayList<>();

        updates.stream().sorted(Comparator.comparing(u -> u.originalFile().getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).forEachOrdered(update -> {
            String from = update.originalFile().getFileName().toString();
            String to = update.update().fileName();
            tableModel.addRow(new Object[]{true, from, "→ " + to});
            rowToUpdate.add(update);
        });

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setMaxWidth(30);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(500, Math.min(rowToUpdate.size() * 30 + 50, 300)));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Select All / None Buttons
        JButton selectAll = new JButton("Select All");
        JButton selectNone = new JButton("Select None");

        selectAll.addActionListener(e -> {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                tableModel.setValueAt(true, i, 0);
            }
        });

        selectNone.addActionListener(e -> {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                tableModel.setValueAt(false, i, 0);
            }
        });

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonsPanel.add(selectAll);
        buttonsPanel.add(selectNone);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.add(new JLabel("Select updates to apply:"), BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(parent, mainPanel, "Mod Updates",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            List<ModUpdate> selected = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                Boolean checked = (Boolean) tableModel.getValueAt(i, 0);
                if (checked != null && checked) {
                    selected.add(rowToUpdate.get(i));
                }
            }
            return selected;
        } else {
            return Collections.emptyList();
        }
    }
}
