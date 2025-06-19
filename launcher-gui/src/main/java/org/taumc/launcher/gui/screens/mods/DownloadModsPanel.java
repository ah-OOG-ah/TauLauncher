package org.taumc.launcher.gui.screens.mods;

import org.taumc.launcher.core.mods.DownloadableFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class DownloadModsPanel extends JPanel {
    final DefaultListModel<DownloadableFile> modListModel;
    private final JList<DownloadableFile> modList;
    final JButton downloadButton;
    final JButton cancelButton;

    public DownloadModsPanel() {
        setLayout(new BorderLayout(10, 10));

        modListModel = new DefaultListModel<>();
        modList = new JList<>(modListModel);
        modList.setVisibleRowCount(10);
        modList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modList.setCellRenderer(new DownloadableFileCellRenderer());

        // Create popup menu
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem removeItem = new JMenuItem("Remove");
        contextMenu.add(removeItem);

        // Hook up listener to remove selected mod
        removeItem.addActionListener(e -> {
            int selectedIndex = modList.getSelectedIndex();
            if (selectedIndex != -1) {
                modListModel.remove(selectedIndex);
            }
        });

        // Add mouse listener for right-click
        modList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }

            private void showContextMenu(MouseEvent e) {
                int index = modList.locationToIndex(e.getPoint());
                if (index != -1 && modList.getCellBounds(index, index).contains(e.getPoint())) {
                    modList.setSelectedIndex(index); // select the item under the cursor
                    contextMenu.show(modList, e.getX(), e.getY());
                }
            }
        });

        add(new JScrollPane(modList), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        downloadButton = new JButton("Download");
        cancelButton = new JButton("Cancel");

        buttonPanel.add(cancelButton);
        buttonPanel.add(downloadButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
}
