package org.taumc.launcher.gui.screens.instance;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.meta.legacyforge.ModInfo;
import org.taumc.launcher.core.mods.DownloadableFile;
import org.taumc.launcher.core.mods.ModHostingSite;
import org.taumc.launcher.core.mods.ModSearchOptions;
import org.taumc.launcher.core.mods.ModUpdate;
import org.taumc.launcher.core.mods.ProjectType;
import org.taumc.launcher.core.mods.curseforge.CurseForgeModHostingSite;
import org.taumc.launcher.core.mods.modrinth.ModrinthModHostingSite;
import org.taumc.launcher.gui.SwingHelpers;
import org.taumc.launcher.gui.launch.LaunchHandler;
import org.taumc.launcher.gui.launch.ProgressDialog;
import org.taumc.launcher.gui.screens.mods.AddModsView;
import org.taumc.launcher.gui.screens.mods.ModUpdateDialog;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModManagerPanel extends JPanel {
    private static final int ICON_SIZE = 24;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModManagerPanel.class);
    public static final List<ModHostingSite<?, ?>> SITES = List.of(new CurseForgeModHostingSite(), new ModrinthModHostingSite());

    private final Path instancePath;
    private final Frame owner;
    private final ListModel<MMCPack.Component> installedComponents;
    private final Map<Path, CompletableFuture<ModMetadata>> iconFutures = new HashMap<>();

    private JTable modTable;
    private ModTableModel modTableModel;
    private JButton removeButton;
    private JButton downloadMoreButton;
    private final ProgressDialog progressDialog;

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

        IntStream.range(0, ModTableModel.COLUMNS.size()).forEach(i -> modTable.getColumnModel().getColumn(i).setPreferredWidth(ModTableModel.COLUMNS.get(i).preferredWidth()));

        // Checkbox editor/renderer already handled by default for Boolean class

        // Enable multiple row selection
        modTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        modTable.addKeyListener(new TableSearchKeyListener());

        JScrollPane tableScrollPane = new JScrollPane(modTable);

        // Sidebar on the right with buttons
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(150, 0));

        removeButton = new JButton("Remove Selected");
        downloadMoreButton = new JButton("Download More");
        var addFileButton = new JButton("Add Local File");
        var showInFolder = new JButton("Show In Folder");
        var checkForUpdates = new JButton("Check For Updates");

        List.of(removeButton, downloadMoreButton, addFileButton, showInFolder, checkForUpdates).forEach(btn -> {
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
        showInFolder.addActionListener(e -> {
            int[] selectedRows = Arrays.stream(modTable.getSelectedRows()).map(modTable::convertRowIndexToModel).toArray();
            for (int i : selectedRows) {
                SwingHelpers.showFileInFolder(modTableModel.mods.get(i).path.toFile());
            }
        });
        checkForUpdates.addActionListener(e -> this.checkForModUpdates());

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

    private ImageIcon readIcon(ZipFile file, String iconPath) throws IOException {
        if (iconPath == null || iconPath.isBlank()) {
            return null;
        }
        var ze = file.getEntry(iconPath);
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
        return null;
    }

    private ModMetadata computeForgeMetadata(ZipEntry modsToml, ZipFile file) throws IOException {
        TomlParseResult toml;
        try (var is = file.getInputStream(modsToml)) {
            toml = Toml.parse(is);
        }
        var mods = toml.getArrayOrEmpty("mods");
        ImageIcon icon = null;
        String name = null, version = null;
        if (!mods.isEmpty()) {
            var modData = mods.getTable(0);
            String logoFile = Objects.requireNonNullElse(modData.get("logoFile"), "").toString();
            name = Objects.requireNonNullElse(modData.get("displayName"), "").toString();
            if (name.isBlank()) {
                name = null;
            }
            version = Objects.requireNonNullElse(modData.get("version"), "").toString();
            if (version.equals("${file.jarVersion}") && file.getEntry("META-INF/MANIFEST.MF") instanceof ZipEntry me) {
                try (var is = file.getInputStream(me)) {
                    Manifest manifest = new Manifest(is);
                    version = Objects.requireNonNullElse(manifest.getMainAttributes().getValue("Implementation-Version"), "");
                }
            }
            icon = readIcon(file, logoFile);
        }
        return new ModMetadata(icon, name, version);
    }

    private ModMetadata computeLegacyForgeMetadata(ZipEntry mcmodInfo, ZipFile file) throws IOException {
        try (var is = file.getInputStream(mcmodInfo)) {
            var mapper = JsonMapper.builder()
                    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, true)
                    .build();
            var node = mapper.readTree(is);
            List<ModInfo> modInfos;
            if (node.isArray()) {
                modInfos = mapper.treeToValue(node, new TypeReference<>() {});
            } else if (node.isObject()) {
                modInfos = mapper.treeToValue(node.get("modList"), new TypeReference<>() {});
            } else {
                return null;
            }
            if (modInfos.isEmpty()) {
                return null;
            }
            var modInfo = modInfos.getFirst();
            return new ModMetadata(readIcon(file, modInfo.logoFile()), modInfo.name(), modInfo.version());
        }
    }

    private record ModMetadata(ImageIcon icon, String name, String version) {}

    private CompletableFuture<ModMetadata> computeMetadata(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try (ZipFile zf = new ZipFile(path.toFile())) {
                var forgeMod = zf.getEntry("META-INF/mods.toml");
                if (forgeMod != null) {
                    return computeForgeMetadata(forgeMod, zf);
                }
                var neoforgeMod = zf.getEntry("META-INF/neoforge.mods.toml");
                if (neoforgeMod != null) {
                    return computeForgeMetadata(neoforgeMod, zf);
                }
                var legacyForgeMod = zf.getEntry("mcmod.info");
                if (legacyForgeMod != null) {
                    return computeLegacyForgeMetadata(legacyForgeMod, zf);
                }
            } catch (Exception e) {
                LOGGER.error("Error computing mod metadata for {}", path.getFileName().toString(), e);
            }
            return null;
        }).whenCompleteAsync((m, t) -> this.modTable.repaint(), SwingUtilities::invokeLater);
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
        var modFiles = modTableModel.mods.stream().map(m -> m.path).toList();
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
                    JOptionPane.showMessageDialog(this.owner, "There are no updates available", "Updater", JOptionPane.INFORMATION_MESSAGE);
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

        public Mod(Path path) {
            this.metadata = iconFutures.computeIfAbsent(path, ModManagerPanel.this::computeMetadata);
            this.path = path;
        }

        public boolean enabled() {
            return !path.getFileName().toString().endsWith(".disabled");
        }

        public ModMetadata metadata() {
            var meta = metadata.getNow(null);
            if (meta == null) {
                return new ModMetadata(null, path.getFileName().toString(), "");
            } else {
                return meta;
            }
        }
    }

    // Table model
    private static class ModTableModel extends AbstractTableModel {
        public record Column(String name, Class<?> clz, boolean fixed, int preferredWidth, Function<Mod, Object> valueGetter) {}
        public static final List<Column> COLUMNS = List.of(
                new Column("Enable", Boolean.class, true, 60, m -> m.enabled()),
                new Column("Icon", Icon.class, true, 40, m -> m.metadata().icon()),
                new Column("Name", String.class, false, 200, m -> m.metadata().name()),
                new Column("Version", String.class, true, 80, m -> m.metadata().version())
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
