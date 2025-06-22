package org.taumc.launcher.gui.screens.instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.gui.SwingHelpers;
import org.taumc.launcher.gui.launch.LaunchHandler;
import org.taumc.launcher.gui.screens.mods.AddModsView;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModManagerPanel extends JPanel {
    private static final int ICON_SIZE = 24;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModManagerPanel.class);

    private final Path instancePath;
    private final Frame owner;
    private final ListModel<MMCPack.Component> installedComponents;
    private final Map<Path, CompletableFuture<ImageIcon>> iconFutures = new HashMap<>();

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
        modTable.setRowHeight(ICON_SIZE); // make row tall enough for images
        modTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        modTable.setAutoCreateRowSorter(true);

        SwingUtilities.invokeLater(() -> {
            TableRowSorter<?> sorter = (TableRowSorter<?>) modTable.getRowSorter();
            sorter.setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.ASCENDING)));
            sorter.sort();
        });

        modTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        modTable.getColumnModel().getColumn(1).setPreferredWidth(40);
        modTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        modTable.getColumnModel().getColumn(3).setPreferredWidth(80);

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
                int fixedWidth = IntStream.range(0, ModTableModel.COLUMNS.size()).map(i -> ModTableModel.COLUMNS.get(i).fixed() ? columnModel.getColumn(i).getPreferredWidth() : 0).sum();

                // Subtract column margins (if any)
                int columnMargin = columnModel.getColumnMargin() * columnModel.getColumnCount();
                fixedWidth += columnMargin;

                // Calculate available width for middle column
                int middleWidth = totalWidth - fixedWidth;

                if (middleWidth < 50) middleWidth = 50; // minimal width guard

                int middleWidthPerColumn = middleWidth / (int)ModTableModel.COLUMNS.stream().filter(c -> !c.fixed()).count();
                IntStream.range(0, ModTableModel.COLUMNS.size()).filter(i -> !ModTableModel.COLUMNS.get(i).fixed()).forEach(i -> columnModel.getColumn(i).setPreferredWidth(middleWidthPerColumn));

                // Force table to re-layout columns
                modTable.doLayout();
            }
        });
    }

    private Path getModsFolder() {
        return LaunchHandler.computeMinecraftFolder(instancePath).resolve("mods");
    }


    private ImageIcon computeForgeModIcon(ZipEntry modsToml, ZipFile file) throws IOException {
        TomlParseResult toml;
        try (var is = file.getInputStream(modsToml)) {
            toml = Toml.parse(is);
        }
        var mods = toml.getArrayOrEmpty("mods");
        if (!mods.isEmpty()) {
            String logoFile = Objects.requireNonNullElse(mods.getTable(0).get("logoFile"), "").toString();
            if (!logoFile.isBlank()) {
                var ze = file.getEntry(logoFile);
                if (ze != null) {
                    try (var is = file.getInputStream(ze)) {
                        BufferedImage img = ImageIO.read(is);
                        if (img != null) {
                            var scaled = img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
                            img.flush();
                            return new ImageIcon(scaled);
                        }
                    }
                }
            }
        }
        return null;
    }

    private CompletableFuture<ImageIcon> computeModIcon(Path path) {
        if (true) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            try (ZipFile zf = new ZipFile(path.toFile())) {
                var forgeMod = zf.getEntry("META-INF/mods.toml");
                if (forgeMod != null) {
                    return computeForgeModIcon(forgeMod, zf);
                }
                var neoforgeMod = zf.getEntry("META-INF/neoforge.mods.toml");
                if (neoforgeMod != null) {
                    return computeForgeModIcon(neoforgeMod, zf);
                }
            } catch (Exception e) {
                LOGGER.error("Error computing mod icon for {}", path.getFileName().toString(), e);
            }
            return null;
        });
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
                    var mod = new Mod(enabled, iconFutures.computeIfAbsent(filePath, this::computeModIcon), name, "unknown", filePath);
                    modTableModel.addMod(mod);
                });
            }
        } catch (IOException e) {
            modTableModel.clear();
        }
        modTable.clearSelection();
    }

    private void removeSelectedMods() {
        int[] selectedRows = Arrays.stream(modTable.getSelectedRows()).map(modTable::convertRowIndexToModel).toArray();
        Arrays.sort(selectedRows);
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
        CompletableFuture<ImageIcon> icon;
        String name;
        String version;
        Path path;

        public Mod(boolean enabled, CompletableFuture<ImageIcon> icon, String name, String version, Path path) {
            this.enabled = enabled;
            this.icon = icon;
            this.name = name;
            this.version = version;
            this.path = path;
        }
    }

    // Table model
    private static class ModTableModel extends AbstractTableModel {
        public record Column(String name, Class<?> clz, boolean fixed) {}
        public static final List<Column> COLUMNS = List.of(
                new Column("Enable", Boolean.class, true),
                new Column("Icon", Icon.class, true),
                new Column("Name", String.class, false),
                new Column("Version", String.class, true)
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
            return COLUMNS.size();
        }

        @Override
        public String getColumnName(int col) {
            return COLUMNS.get(col).name();
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return COLUMNS.get(col).clz();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Mod mod = mods.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> mod.enabled;
                case 1 -> mod.icon.getNow(null);
                case 2 -> mod.name;
                case 3 -> mod.version;
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
