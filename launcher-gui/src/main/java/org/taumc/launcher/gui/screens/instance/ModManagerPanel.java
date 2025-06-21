package org.taumc.launcher.gui.screens.instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.gui.SwingHelpers;
import org.taumc.launcher.gui.launch.LaunchHandler;
import org.taumc.launcher.gui.screens.mods.AddModsView;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ModManagerPanel extends JPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModManagerPanel.class);

    private final Path instancePath;
    private final Frame owner;
    private final ListModel<MMCPack.Component> installedComponents;

    private JTable modTable;
    private ModTableModel modTableModel;
    private JButton removeButton;
    private JButton downloadMoreButton;

    public ModManagerPanel(Path instancePath, Frame owner, ListModel<MMCPack.Component> installedComponents) {
        this.instancePath = instancePath;
        this.owner = owner;
        this.installedComponents = installedComponents;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create the mod table model and table
        modTableModel = new ModTableModel();
        modTable = new JTable(modTableModel) {
            // Override getColumnClass so JTable knows how to render each column correctly
            @Override
            public Class<?> getColumnClass(int column) {
                return modTableModel.getColumnClass(column);
            }
        };
        modTable.setRowHeight(24); // make row tall enough for images
        modTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        modTable.setAutoCreateRowSorter(true);

        SwingUtilities.invokeLater(() -> {
            TableRowSorter<?> sorter = (TableRowSorter<?>) modTable.getRowSorter();
            sorter.setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.ASCENDING)));
            sorter.sort();
        });

        modTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        modTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        modTable.getColumnModel().getColumn(2).setPreferredWidth(80);

        // Checkbox editor/renderer already handled by default for Boolean class

        // Enable multiple row selection
        modTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JScrollPane tableScrollPane = new JScrollPane(modTable);

        // Sidebar on the right with buttons
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(150, 0));

        removeButton = new JButton("Remove Selected");
        downloadMoreButton = new JButton("Download More");
        var addFileButton = new JButton("Add Local File");

        List.of(removeButton, downloadMoreButton, addFileButton).forEach(btn -> {
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            sidebar.add(btn);
            sidebar.add(Box.createVerticalStrut(15));
        });

        add(tableScrollPane, BorderLayout.CENTER);
        add(sidebar, BorderLayout.EAST);

        // Sample data
        refreshTableModel();

        // Button actions
        removeButton.addActionListener(e -> removeSelectedMods());
        downloadMoreButton.addActionListener(e -> downloadMoreMods());
        addFileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setMultiSelectionEnabled(true);
            fileChooser.setFileFilter(new FileNameExtensionFilter("Mods", "jar"));
            int result = fileChooser.showOpenDialog(null); // or use a parent component

            if (result == JFileChooser.APPROVE_OPTION) {
                Path modsFolder = getModsFolder();
                File[] files = fileChooser.getSelectedFiles();
                for (var f : files) {
                    var path = f.toPath();
                    try {
                        Files.copy(path, modsFolder.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(null, ex.getMessage(), "Error copying mod", JOptionPane.ERROR_MESSAGE);
                    }
                }
                this.refreshTableModel();
            }
        });

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int totalWidth = tableScrollPane.getViewport().getWidth();

                var columnModel = modTable.getColumnModel();

                // Calculate width taken by fixed columns
                int fixedWidth = columnModel.getColumn(0).getPreferredWidth()
                        + columnModel.getColumn(2).getPreferredWidth();

                // Subtract column margins (if any)
                int columnMargin = columnModel.getColumnMargin() * columnModel.getColumnCount();
                fixedWidth += columnMargin;

                // Calculate available width for middle column
                int middleWidth = totalWidth - fixedWidth;

                if (middleWidth < 50) middleWidth = 50; // minimal width guard

                columnModel.getColumn(1).setPreferredWidth(middleWidth);

                // Force table to re-layout columns
                modTable.doLayout();
            }
        });
    }

    private Path getModsFolder() {
        return LaunchHandler.computeMinecraftFolder(instancePath).resolve("mods");
    }

    private void refreshTableModel() {
        var modsFolder = getModsFolder();
        modTableModel.clear();
        modTable.clearSelection();
        try {
            Files.createDirectories(modsFolder);

            try(Stream<Path> stream = Files.list(modsFolder)) {
                stream.filter(p -> !Files.isDirectory(p)).forEach(filePath -> {
                    String name = filePath.getFileName().toString();
                    boolean enabled = !name.endsWith(".disabled");
                    if (!enabled) {
                        name = name.substring(0, name.length() - 9);
                    }
                    var mod = new Mod(enabled, null, name, "unknown", filePath);
                    modTableModel.addMod(mod);
                });
            }
        } catch (IOException e) {
            modTableModel.clear();
        }
        modTable.clearSelection();
    }

    private void removeSelectedMods() {
        int[] selectedRows = modTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "No mods selected to remove.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Remove in reverse order to avoid shifting indices problem
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            var mod = modTableModel.removeMod(selectedRows[i]);
            if (mod != null) {
                try {
                    Files.delete(mod.path);
                } catch (IOException e) {
                    LOGGER.error("Error deleting mod", e);
                }
            }
        }
    }

    private void downloadMoreMods() {
        var addModsView = new AddModsView(this.owner, this.getModsFolder(), SwingHelpers.immutableListOf(this.installedComponents));
        addModsView.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                refreshTableModel();
            }
        });
    }

    // Mod data class
    private static class Mod {
        boolean enabled;
        ImageIcon icon;
        String name;
        String version;
        Path path;

        public Mod(boolean enabled, ImageIcon icon, String name, String version, Path path) {
            this.enabled = enabled;
            this.icon = icon;
            this.name = name;
            this.version = version;
            this.path = path;
        }
    }

    // Table model
    private static class ModTableModel extends AbstractTableModel {
        private record Column(String name, Class<?> clz) {}
        private final List<Column> columns = List.of(
                new Column("Enable", Boolean.class),
                new Column("Name", String.class),
                new Column("Version", String.class)
        );
        private final java.util.List<Mod> mods = new ArrayList<>();

        public void addMod(Mod mod) {
            mods.add(mod);
            int row = mods.size() - 1;
            fireTableRowsInserted(row, row);
        }

        public Mod removeMod(int rowIndex) {
            Mod mod = null;
            if (rowIndex >= 0 && rowIndex < mods.size()) {
                mod = mods.remove(rowIndex);
                fireTableRowsDeleted(rowIndex, rowIndex);
            }
            return mod;
        }

        public void clear() {
            if (mods.isEmpty()) {
                return;
            }
            var size = mods.size();
            mods.clear();
            fireTableRowsDeleted(0, size - 1);
        }

        @Override
        public int getRowCount() {
            return mods.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public String getColumnName(int col) {
            return columns.get(col).name();
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return columns.get(col).clz();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Mod mod = mods.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> mod.enabled;
                case 1 -> mod.name;
                case 2 -> mod.version;
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int colIndex) {
            // Only the "Enable" checkbox column is editable
            return colIndex == 0;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int colIndex) {
            if (rowIndex < 0 || rowIndex >= mods.size()) return;
            Mod mod = mods.get(rowIndex);
            if (colIndex == 0 && value instanceof Boolean) {
                mod.enabled = (Boolean) value;
                fireTableCellUpdated(rowIndex, colIndex);
            }
        }
    }
}
