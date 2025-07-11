package org.taumc.launcher.gui.screens.instance;

import com.formdev.flatlaf.FlatClientProperties;
import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.mods.ModHostingSite;
import org.taumc.launcher.core.mods.ModSearchOptions;
import org.taumc.launcher.core.mods.ModUpdate;
import org.taumc.launcher.core.mods.ProjectType;
import org.taumc.launcher.core.mods.curseforge.CurseForgeModHostingSite;
import org.taumc.launcher.core.mods.metadata.ModMetadata;
import org.taumc.launcher.core.mods.modpacksch.ModpacksCHHostingSite;
import org.taumc.launcher.core.mods.modrinth.ModrinthModHostingSite;
import org.taumc.launcher.gui.SwingHelpers;
import org.taumc.launcher.gui.components.Debounce;
import org.taumc.launcher.gui.components.FileChooser;
import org.taumc.launcher.gui.components.SwingFileChooser;
import org.taumc.launcher.gui.launch.LaunchHandler;
import org.taumc.launcher.gui.launch.ProgressDialog;
import org.taumc.launcher.gui.screens.mods.AddModsView;
import org.taumc.launcher.gui.screens.mods.ModUpdateDialog;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class ModManagerPanel extends JPanel {
    private static final int ICON_SIZE = 24;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModManagerPanel.class);
    public static final List<ModHostingSite<?, ?>> SITES = List.of(new CurseForgeModHostingSite(), new ModrinthModHostingSite(), new ModpacksCHHostingSite());

    private final Path instancePath;
    private final Frame owner;
    private final ListModel<MMCPack.Component> installedComponents;
    private final Map<Path, CompletableFuture<ModMetadata>> metadataFutures = new HashMap<>();
    private record ModIcon(Path jarPath, String logoFile) {}
    private final Map<ModIcon, CompletableFuture<ImageIcon>> iconFutures = new HashMap<>();

    private JTable modTable;
    private ModTableModel modTableModel;
    private JButton removeButton;
    private JButton downloadMoreButton;
    private final ProgressDialog progressDialog;
    private final Debounce sortDebounce = new Debounce(500);

    public ModManagerPanel(Path instancePath, Frame owner, ListModel<MMCPack.Component> installedComponents) {
        this.instancePath = instancePath;
        this.owner = owner;
        this.progressDialog = new ProgressDialog(owner);
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
            sorter.setSortKeys(List.of(new RowSorter.SortKey(ModTableModel.NAME_COLUMN_INDEX, SortOrder.ASCENDING)));
            sorter.sort();
        });

        IntStream.range(0, ModTableModel.COLUMNS.size()).forEach(i -> {
            var column = modTable.getColumnModel().getColumn(i);
            var columnSpec = ModTableModel.COLUMNS.get(i);
            column.setPreferredWidth(columnSpec.preferredWidth());
            if (columnSpec.renderer() != null) {
                column.setCellRenderer(columnSpec.renderer().get());
            }
        });

        // Checkbox editor/renderer already handled by default for Boolean class

        // Enable multiple row selection
        modTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        modTable.addKeyListener(new TableSearchKeyListener());

        // Enable drop target
        modTable.setFillsViewportHeight(true);
        modTable.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;

                try {
                    Transferable t = support.getTransferable();
                    List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    addModFiles(files);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(modTable);

        // Deselect table items when anything else in panel is clicked
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.getDeepestComponentAt(ModManagerPanel.this, e.getX(), e.getY()) instanceof JPanel) {
                    modTable.clearSelection();
                }
            }
        });

        // Sidebar on the right with buttons
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(150, 0));

        removeButton = new JButton("Remove Selected");
        downloadMoreButton = new JButton("Download More");
        var addFileButton = new JButton("Add Local File");
        var showInFolder = new JButton("View Mods Folder");
        var checkForUpdates = new JButton("Check For Updates");
        var viewConfigs = new JButton("View Configs Folder");

        List.of(downloadMoreButton, checkForUpdates, addFileButton, removeButton, checkForUpdates, viewConfigs, showInFolder).forEach(btn -> {
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
            sidebar.add(btn);
            sidebar.add(Box.createVerticalStrut(2));
        });

        add(tableScrollPane, BorderLayout.CENTER);
        add(sidebar, BorderLayout.EAST);

        // Sample data
        refreshTableModel();

        // Button actions
        removeButton.addActionListener(e -> removeSelectedMods());
        downloadMoreButton.addActionListener(e -> downloadMoreMods());
        addFileButton.addActionListener(e -> {
            FileChooser chooser = FileChooser.getInstance();
            chooser.setAllowMultiSelection(true);
            if (chooser instanceof SwingFileChooser sfc) {
                sfc.fileChooser.setFileFilter(new FileNameExtensionFilter("Mods", "jar"));
            }

            var files = chooser.showFilePicker();
            if (!files.isEmpty()) {
                addModFiles(files);
            }
        });
        showInFolder.addActionListener(ev -> {
            int[] selectedRows = Arrays.stream(modTable.getSelectedRows()).map(modTable::convertRowIndexToModel).toArray();
            if (selectedRows.length == 0) {
                try {
                    Desktop.getDesktop().open(getModsFolder().toFile());
                } catch (IOException e) {
                    LOGGER.error("Error opening folder", e);
                }
            } else {
                for (int i : selectedRows) {
                    SwingHelpers.showFileInFolder(modTableModel.mods.get(i).path.toFile());
                }
            }
        });
        checkForUpdates.addActionListener(e -> this.checkForModUpdates());
        viewConfigs.addActionListener(ev -> {
            try {
                Desktop.getDesktop().open(getModsFolder().getParent().resolve("config").toFile());
            } catch (IOException e) {
                LOGGER.error("Error opening folder", e);
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

    private void addModFiles(List<File> files) {
        Path modsFolder = getModsFolder();
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

    private Path getModsFolder() {
        return LaunchHandler.computeMinecraftFolder(instancePath).resolve("mods");
    }

    private CompletableFuture<ImageIcon> readIcon(ModIcon modIcon) {
        return CompletableFuture.supplyAsync(() -> {
            try (var file = new ZipFile(modIcon.jarPath.toFile())) {
                var ze = file.getEntry(modIcon.logoFile());
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
            } catch (IOException e) {
                LOGGER.error("Error reading icon", e);
            }
            return null;
        }).whenCompleteAsync((m, t) -> {
            this.modTable.repaint();
        }, SwingUtilities::invokeLater);
    }


    private CompletableFuture<ModMetadata> computeMetadata(Path path) {
        return ModMetadata.computeFor(path).whenCompleteAsync((m, t) -> {
            sortDebounce.call(() -> ((TableRowSorter<?>)this.modTable.getRowSorter()).sort());
            this.modTable.repaint();
        }, SwingUtilities::invokeLater);
    }

    private void refreshTableModel() {
        var modsFolder = getModsFolder();
        modTableModel.clear();
        modTable.clearSelection();
        try {
            Files.createDirectories(modsFolder);

            try(Stream<Path> stream = Files.list(modsFolder)) {
                stream.filter(p -> !Files.isDirectory(p)).forEach(filePath -> {
                    var mod = new Mod(filePath);
                    modTableModel.addMod(mod);
                });
            }
        } catch (IOException e) {
            modTableModel.clear();
        }
        modTable.clearSelection();
    }

    private void checkForModUpdates() {
        Stream<Mod> modStream;
        if (modTable.getSelectedRowCount() == 0) {
            modStream = modTableModel.mods.stream();
        } else {
            modStream = IntStream.of(modTable.getSelectedRows()).map(modTable::convertRowIndexToModel).mapToObj(i -> modTableModel.mods.get(i));
        }

        var modFiles = modStream.map(m -> m.path).toList();
        int numModsToCheckForUpdates = modFiles.size();

        List<CompletableFuture<List<ModUpdate>>> futures = new ArrayList<>();
        ModSearchOptions searchOptions = new ModSearchOptions();
        searchOptions.componentFilter = SwingHelpers.immutableListOf(this.installedComponents);
        searchOptions.projectType = ProjectType.MOD;
        var task = this.progressDialog.addTask("Checking for updates");
        try {
            for (var site : SITES) {
                futures.add(site.getModUpdates(modFiles, searchOptions, this.progressDialog));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenCompleteAsync((c, t) -> {
                task.close();
                if (t != null) {
                    LOGGER.error("Error checking for updates", t);
                    JOptionPane.showMessageDialog(this.owner, t.toString(), "Error checking for updates", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                var updates = futures.stream().map(CompletableFuture::join).flatMap(Collection::stream)
                        .collect(Collectors.toMap(ModUpdate::originalFile, Function.identity(), (a, b) -> b))
                        .values()
                        .stream()
                        .sorted(Comparator.comparing(u -> u.originalFile().getFileName().toString()))
                        .toList();
                if (updates.isEmpty()) {
                    JOptionPane.showMessageDialog(this.owner, "There are no updates available for all " + numModsToCheckForUpdates + " selected mods", "Updater", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                var selectedUpdates = ModUpdateDialog.showModUpdateDialog(this.owner, updates);
                ModUpdate.applyUpdates(selectedUpdates, this.progressDialog).whenCompleteAsync(($, t2) -> {
                    this.refreshTableModel();
                    if (t2 != null) {
                        JOptionPane.showMessageDialog(this.owner, t2.toString(), "Error downloading updates", JOptionPane.ERROR_MESSAGE);
                    }
                }, SwingUtilities::invokeLater);
            }, SwingUtilities::invokeLater);
        } catch (Exception e) {
            task.close();
        }
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
        var addModsView = new AddModsView(this.owner, SwingHelpers.immutableListOf(this.installedComponents), ProjectType.MOD, files -> {
            var client = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (var file : files) {
                try {
                    URI uri = new URI(file.downloadUrl());
                    var task = this.progressDialog.addTask(file.fileName());
                    Path destination = this.getModsFolder().resolve(file.fileName());
                    var handler = DownloadProgressTracker.track(HttpResponse.BodyHandlers.ofFile(destination), task);
                    futures.add(client.sendAsync(HttpRequest.newBuilder().uri(uri).build(), handler).whenComplete((c, t) -> task.close()));
                } catch (Exception e) {
                    futures.add(CompletableFuture.failedFuture(e));
                }
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((c, t) -> client.close());
        });
        addModsView.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                refreshTableModel();
            }
        });
    }

    // Mod data class
    private class Mod {
        private final CompletableFuture<ModMetadata> metadata;
        private final Path path;
        private final String fileName;
        private final ModMetadata fallbackMetadata;

        public Mod(Path path) {
            this.metadata = metadataFutures.computeIfAbsent(path, ModManagerPanel.this::computeMetadata);
            this.path = path;
            this.fileName = path.getFileName().toString();
            this.fallbackMetadata = ModMetadata.builder().name(fileName).build();
        }

        public boolean enabled() {
            return !fileName.endsWith(".disabled");
        }

        public ModMetadata metadata() {
            var meta = metadata.getNow(null);
            // This cannot be inlined into the call above, because the future might complete with a value of null
            return Objects.requireNonNullElse(meta, fallbackMetadata);
        }

        public ImageIcon icon() {
            var meta = metadata();
            if (meta.logoPath() == null) {
                return null;
            }
            return iconFutures.computeIfAbsent(new ModIcon(this.path, meta.logoPath()), ModManagerPanel.this::readIcon).getNow(null);
        }

        public String friendlyName() {
            var meta = metadata();
            if (meta.name() == null) {
                return fileName;
            }
            return meta.name();
        }

        public long size() {
            try {
                return Files.size(path);
            } catch (IOException e) {
                LOGGER.error("Error reading file size", e);
                return 0;
            }
        }

        public void toggleEnablement() {
            String oldName = this.fileName;
            String newName = this.enabled() ? (oldName + ".disabled") : oldName.replaceFirst("\\.disabled$", "");
            try {
                Path newPath = this.path.resolveSibling(newName);
                Files.move(this.path, newPath);
                var newMod = new Mod(newPath);
                modTableModel.replaceMod(this, newMod);
            } catch (IOException e) {
                LOGGER.error("Error toggling mod enablement", e);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Mod mod = (Mod) o;
            return Objects.equals(path, mod.path);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(path);
        }
    }

    // Table model
    private static class ModTableModel extends AbstractTableModel {
        public record Column(String name, Class<?> clz, boolean fixed, int preferredWidth, Function<Mod, Object> valueGetter, Supplier<TableCellRenderer> renderer) { }
        public static final List<Column> COLUMNS = List.of(
                new Column("Enable", Boolean.class, true, 60, m -> m.enabled(), null),
                new Column("Icon", Icon.class, true, 40, m -> m.icon(), null),
                new Column("Name", String.class, false, 200, m -> m.friendlyName(), null),
                new Column("Version", String.class, true, 80, m -> m.metadata().version(), null),
                new Column("Size", Long.class, true, 80, m -> m.size(), () -> new DefaultTableCellRenderer() {
                    private static String formatSize(long bytes) {
                        if (bytes < 1024) return bytes + " B";
                        int exp = (int) (Math.log(bytes) / Math.log(1024));
                        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
                    }

                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                        if (value instanceof Long size) {
                            this.setText(formatSize(size));
                        }

                        return this;
                    }
                })
        );
        public static final int NAME_COLUMN_INDEX = IntStream.range(0, COLUMNS.size()).filter(i -> COLUMNS.get(i).name().equals("Name")).findFirst().orElseThrow();
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

        public void replaceMod(Mod oldMod, Mod newMod) {
            int i = mods.indexOf(oldMod);
            if (i >= 0) {
                mods.set(i, newMod);
                fireTableRowsUpdated(i, i);
            }
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
            return COLUMNS.get(columnIndex).valueGetter().apply(mod);
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
                mod.toggleEnablement();
                fireTableCellUpdated(rowIndex, colIndex);
            }
        }
    }

    private class TableSearchKeyListener extends KeyAdapter {
        private final StringBuilder typed = new StringBuilder();
        private long lastType = 0;
        private static final long SEARCH_TIMEOUT = TimeUnit.SECONDS.toNanos(1);

        @Override
        public void keyTyped(KeyEvent e) {
            char ch = e.getKeyChar();
            if (Character.isISOControl(ch))
                return;

            if ((System.nanoTime() - lastType) >= SEARCH_TIMEOUT) {
                typed.setLength(0);
            }

            lastType = System.nanoTime();

            typed.append(ch);

            String prefix = typed.toString().toLowerCase(Locale.ROOT);
            for (int row = 0; row < modTable.getRowCount(); row++) {
                Object val = modTable.getValueAt(row, ModTableModel.NAME_COLUMN_INDEX);
                if (val != null && val.toString().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    modTable.changeSelection(row, ModTableModel.NAME_COLUMN_INDEX, false, false);
                    break;
                }
            }
        }
    }
}
