package org.taumc.launcher.gui.screens.mods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.mods.DownloadableFile;
import org.taumc.launcher.core.mods.Mod;
import org.taumc.launcher.core.mods.ModHostingSite;
import org.taumc.launcher.core.mods.ModSearchOptions;
import org.taumc.launcher.core.mods.ProjectType;
import org.taumc.launcher.core.mods.curseforge.CurseForgeModHostingSite;
import org.taumc.launcher.core.mods.modrinth.ModrinthModHostingSite;
import org.taumc.launcher.gui.SwingHelpers;
import org.taumc.launcher.gui.icon.IconUtil;
import org.taumc.launcher.gui.launch.ProgressDialog;
import org.taumc.launcher.gui.screens.instance.ModManagerPanel;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AddModsView extends JFrame {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddModsView.class);
    private final Map<String, CompletableFuture<ImageIcon>> modIcons = new HashMap<>();
    private final List<MMCPack.Component> installedComponents;
    private final ProjectType projectType;
    private final Function<List<DownloadableFile>, CompletableFuture<Void>> filesConsumer;

    private record ModEntry<M extends Mod, F extends DownloadableFile>(ModHostingSite<M, F> site, M mod, ModSearchOptions searchOptions) {
        CompletableFuture<String> getDescription() {
            return site.getModDescriptionHTML(mod);
        }

        CompletableFuture<List<F>> getAvailableFiles() {
            return site.getModFiles(mod, searchOptions).thenApply(list -> {
                return list.stream().sorted(Comparator.comparing(DownloadableFile::releaseTime).reversed()).toList();
            });
        }
    }

    private JList<ModEntry<?, ?>> searchResultsList;
    private JList<ModHostingSite<?, ?>> sourceList;
    private JEditorPane modInfoTextArea;
    private JScrollPane infoScrollPane;
    private JComboBox<DownloadableFile> filesComboBox;
    private JButton downloadSelectButton;
    private JTextField searchField;
    private DownloadModsPanel downloadModsPanel;

    private boolean allowMultipleSelection = true;

    private final ProgressDialog progressDialog;

    public AddModsView(Frame owner, List<MMCPack.Component> installedComponents, ProjectType projectType, Function<List<DownloadableFile>, CompletableFuture<Void>> filesConsumer) {
        super("Mod Search");
        this.installedComponents = installedComponents;
        this.projectType = projectType;
        this.filesConsumer = filesConsumer;
        initUI();
        setSize(800, 500);
        setLocationRelativeTo(owner);
        this.progressDialog = new ProgressDialog(owner);
        setVisible(true);
    }

    private void initUI() {
        // Main container layout: BorderLayout
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        // Sidebar (left): Buttons to select mod hosting site
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        sidebar.setPreferredSize(new Dimension(150, 0));

        // Create entries with icons

        sourceList = new JList<>(ModManagerPanel.SITES.stream().filter(s -> s.getProjectTypes().contains(ProjectType.MODPACK)).toArray(ModHostingSite[]::new));
        sourceList.setCellRenderer(new SourceEntryRenderer());
        sourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceList.setSelectedIndex(0);

        sidebar.add(sourceList);

        contentPane.add(sidebar, BorderLayout.WEST);

        // Search view (right), split horizontally into two halves
        JSplitPane searchSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        searchSplitPane.setResizeWeight(0.5); // split equally by default

        // Left half: search results list
        var resultsModel = new DefaultListModel<ModEntry<?, ?>>();
        searchResultsList = new JList<>(resultsModel);
        searchResultsList.setCellRenderer(new ModSearchEntryRenderer());
        JScrollPane resultsScrollPane = new JScrollPane(searchResultsList);

        searchSplitPane.setLeftComponent(resultsScrollPane);

        // Right half: mod info text area
        modInfoTextArea = new JEditorPane();
        modInfoTextArea.setContentType("text/html");
        modInfoTextArea.setEditable(false);
        infoScrollPane = new JScrollPane(modInfoTextArea);
        JPanel infoAndFilesDropdownPanel = new JPanel(new BorderLayout());
        filesComboBox = new JComboBox<>(new DefaultComboBoxModel<>());
        filesComboBox.setEditable(false);
        filesComboBox.setRenderer(new DownloadableFileCellRenderer());

        downloadSelectButton = new JButton();
        downloadSelectButton.setEnabled(false);

        updateDownloadButton();

        downloadSelectButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        downloadSelectButton.addActionListener(e -> {
            if (filesComboBox.getSelectedItem() instanceof DownloadableFile file) {
                String ourId = file.getParentModId();
                boolean didRemove = false;
                for (int i = downloadModsPanel.modListModel.size() - 1; i >= 0; i--) {
                    if (downloadModsPanel.modListModel.getElementAt(i).getParentModId().equals(ourId)) {
                        downloadModsPanel.modListModel.remove(i);
                        didRemove = true;
                    }
                }
                if (!didRemove) {
                    if (!downloadModsPanel.modListModel.isEmpty() && !allowMultipleSelection) {
                        JOptionPane.showMessageDialog(this, "Can only add one item at a time", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    downloadModsPanel.modListModel.addElement(file);
                    Set<String> existingDeps = IntStream.range(0, downloadModsPanel.modListModel.size())
                            .mapToObj(downloadModsPanel.modListModel::getElementAt)
                            .map(DownloadableFile::getParentModId)
                            .collect(Collectors.toSet());
                    var depFuture = file.getDependencies(installedComponents, existingDeps);
                    if (!depFuture.isDone()) {
                        var depTask = progressDialog.addTask("Locating dependencies");
                        depFuture.whenComplete((l, t) -> depTask.close());
                    }
                    depFuture.whenCompleteAsync((extraFiles, t) -> {
                        if (extraFiles != null) {
                            downloadModsPanel.modListModel.addAll(extraFiles);
                            updateDownloadButton();
                        }
                    }, SwingUtilities::invokeLater);
                }
                updateDownloadButton();
            }
        });
        downloadSelectButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, downloadSelectButton.getPreferredSize().height));

        JPanel fileControls = new JPanel();
        fileControls.setLayout(new BorderLayout());

        fileControls.add(filesComboBox, BorderLayout.NORTH);
        fileControls.add(downloadSelectButton, BorderLayout.CENTER);

        filesComboBox.addActionListener(e -> this.updateDownloadButton());

        infoAndFilesDropdownPanel.add(infoScrollPane, BorderLayout.CENTER);
        infoAndFilesDropdownPanel.add(fileControls, BorderLayout.SOUTH);
        searchSplitPane.setRightComponent(infoAndFilesDropdownPanel);

        JPanel searchAndModListPanel = new JPanel(new BorderLayout());

        searchField = new JTextField();
        JButton searchButton = new JButton("Search");

        searchField.addActionListener(e -> searchButton.doClick());

        searchButton.addActionListener(e -> triggerSearch());

        JPanel searchBar = new JPanel(new BorderLayout(5, 5));
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchButton, BorderLayout.EAST);
        searchBar.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        searchAndModListPanel.add(searchBar, BorderLayout.NORTH);
        searchAndModListPanel.add(searchSplitPane, BorderLayout.CENTER);

        contentPane.add(searchAndModListPanel, BorderLayout.CENTER);

        this.downloadModsPanel = new DownloadModsPanel();
        this.downloadModsPanel.cancelButton.addActionListener(e -> this.dispose());
        this.downloadModsPanel.downloadButton.addActionListener(e -> this.downloadSelectedMods());
        this.downloadModsPanel.modListModel.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                searchResultsList.repaint();
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                searchResultsList.repaint();

            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                searchResultsList.repaint();
            }
        });
        contentPane.add(this.downloadModsPanel, BorderLayout.SOUTH);

        sourceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                resultsModel.clear();
                triggerSearch();
            }
        });

        searchResultsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ModEntry<?, ?> selected = searchResultsList.getSelectedValue();
                modInfoTextArea.setText("");
                modInfoTextArea.setCaretPosition(0);
                DefaultComboBoxModel<DownloadableFile> model = (DefaultComboBoxModel<DownloadableFile>)filesComboBox.getModel();
                model.removeAllElements();
                if (selected != null) {
                    selected.getDescription().thenAcceptAsync(str -> {
                        modInfoTextArea.setText(str);
                        modInfoTextArea.setCaretPosition(0); // move caret to start
                    }, SwingUtilities::invokeLater);
                    selected.getAvailableFiles().thenAcceptAsync(fileList -> {
                        model.addAll(fileList);
                        if (!fileList.isEmpty()) {
                            filesComboBox.setSelectedIndex(0);
                        }
                    }, SwingUtilities::invokeLater);
                }
            }
        });

        searchResultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = searchResultsList.locationToIndex(e.getPoint());
                    if (index != -1 && index == searchResultsList.getSelectedIndex()) {
                        downloadSelectButton.doClick();
                    }
                }
            }
        });

        triggerSearch();
    }

    public void setAllowMultipleSelection(boolean flag) {
        this.allowMultipleSelection = flag;
    }

    private void updateDownloadButton() {
        DownloadableFile file = (DownloadableFile)filesComboBox.getSelectedItem();
        downloadSelectButton.setEnabled(file != null && (allowMultipleSelection || isModInSelectedDownloadList(file.getParentModId()) || downloadModsPanel.modListModel.isEmpty()));
        downloadSelectButton.setText((file != null && isModInSelectedDownloadList(file.getParentModId())) ? "Remove from downloads" : "Select file for download");
    }

    private boolean isModInSelectedDownloadList(String modId) {
        boolean isInDownloadList = false;
        for (int i = 0; i < downloadModsPanel.modListModel.size(); i++) {
            if (downloadModsPanel.modListModel.getElementAt(i).getParentModId().equals(modId)) {
                isInDownloadList = true;
                break;
            }
        }
        return isInDownloadList;
    }

    private void triggerSearch() {
        modInfoTextArea.setText("");
        var site = sourceList.getSelectedValue();
        if (site == null) {
            return;
        }
        performSearch(site);
    }

    private <M extends Mod> void performSearch(ModHostingSite<M, ?> site) {
        DefaultListModel<ModEntry<?, ?>> model = (DefaultListModel<ModEntry<?, ?>>) searchResultsList.getModel();
        model.clear();
        var searchOptions = new ModSearchOptions();
        searchOptions.filterText = searchField.getText();
        searchOptions.componentFilter = this.installedComponents.isEmpty() ? null : this.installedComponents;
        searchOptions.projectType = this.projectType;
        site.searchForMods(searchOptions)
                .thenApply(l -> l.stream().sorted(Comparator.comparingInt(Mod::downloadCount).reversed()).map(m -> new ModEntry<>(site, m, searchOptions)).toList())
                .thenAcceptAsync(model::addAll, SwingUtilities::invokeLater);
    }

    private void downloadSelectedMods() {
        var model = this.downloadModsPanel.modListModel;
        List<DownloadableFile> files = new ArrayList<>();

        for (int i = 0; i < model.size(); i++) {
            files.add(model.getElementAt(i));
        }

        this.filesConsumer.apply(files).whenCompleteAsync((v, t) -> {
            if (t != null) {
                LOGGER.error("Error downloading mods", t);
            }
            this.dispose();
        }, SwingUtilities::invokeLater);
    }

    private class ModSearchEntryRenderer extends JPanel implements ListCellRenderer<ModEntry<?, ?>> {
        private static final int ICON_SIZE = 48;
        private final JLabel iconLabel = new JLabel();
        private final JTextArea titleLabel = new JTextArea();
        private final JTextArea summaryLabel = new JTextArea();
        private final Font normalTitleFont, selectedTitleFont;
        private final JPanel textPanel;

        public ModSearchEntryRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            iconLabel.setHorizontalAlignment(JLabel.CENTER);
            iconLabel.setVerticalAlignment(JLabel.CENTER);

            textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.add(titleLabel);
            textPanel.add(summaryLabel);
            textPanel.setOpaque(false);

            normalTitleFont = titleLabel.getFont().deriveFont(Font.BOLD);
            selectedTitleFont = normalTitleFont.deriveFont(Font.BOLD | Font.ITALIC);

            titleLabel.setFont(normalTitleFont);
            titleLabel.setOpaque(false);
            summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, 12f));
            summaryLabel.setOpaque(false);

            add(iconLabel, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ModEntry<?, ?>> list,
                                                      ModEntry<?, ?> container,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            var entry = container.mod();
            iconLabel.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
            int labelWidth = list.getWidth() - ICON_SIZE - 20 - 20;
            titleLabel.setFont(isModInSelectedDownloadList(container.mod().modId()) ? selectedTitleFont : normalTitleFont);
            titleLabel.setText(SwingHelpers.ellipsize(titleLabel.getFontMetrics(titleLabel.getFont()), entry.name(), labelWidth));
            summaryLabel.setText(SwingHelpers.ellipsize(summaryLabel.getFontMetrics(summaryLabel.getFont()), entry.summary(), labelWidth));
            if (!entry.smallIconUrl().isEmpty()) {
                var future = modIcons.computeIfAbsent(entry.smallIconUrl(), url -> CompletableFuture.supplyAsync(() -> IconUtil.loadIconFromURL(url, ICON_SIZE)).whenCompleteAsync((c, t) -> AddModsView.this.searchResultsList.repaint(), SwingUtilities::invokeLater));
                iconLabel.setIcon(future.getNow(null));
            } else {
                iconLabel.setIcon(null);
            }

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            titleLabel.setLineWrap(true);
            titleLabel.setWrapStyleWord(true);
            //titleLabel.setSize(list.getWidth(), Short.MAX_VALUE); // set width to list width to calculate height properly

            summaryLabel.setLineWrap(true);
            summaryLabel.setWrapStyleWord(true);
            //summaryLabel.setSize(list.getWidth(), Short.MAX_VALUE); // set width to list width to calculate height properly

            setOpaque(true);
            return this;
        }
    }

    private static class SourceEntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            if (value instanceof ModHostingSite<?, ?> entry) {
                label.setText(entry.name());
                //label.setIcon(entry.icon);
                label.setIconTextGap(10);
            }

            return label;
        }
    }
}
