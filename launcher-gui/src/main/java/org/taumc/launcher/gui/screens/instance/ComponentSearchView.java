package org.taumc.launcher.gui.screens.instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.ComponentSearchQuery;
import org.taumc.launcher.core.meta.component.ComponentSearchResults;
import org.taumc.launcher.core.meta.component.GameComponent;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.MetaRepository;
import org.taumc.launcher.gui.SwingHelpers;
import org.taumc.launcher.gui.components.TauLauncherFrame;
import org.taumc.launcher.gui.icon.IconUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ComponentSearchView extends TauLauncherFrame {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentSearchView.class);

    public record RepoResultPair(MetaRepository repository, ComponentSearchResults.Result result) {}

    private final Collection<MetaRepository> repositories;
    private final Map<String, ReconcilableGameComponent> existingComponents;
    private final Function<List<ComponentCoordinate.Simple>, CompletableFuture<Void>> componentConsumer;

    private final Map<String, CompletableFuture<ImageIcon>> modIcons = new HashMap<>();

    private final JTextField searchField = new JTextField();
    private final JButton searchButton = new JButton("Search");

    private final DefaultListModel<RepoResultPair> resultListModel = new DefaultListModel<>();
    private final JList<RepoResultPair> resultList = new JList<>(resultListModel);

    private final JEditorPane descriptionArea = new JEditorPane();
    private final JComboBox<GameComponent> versionDropdown = new JComboBox<>();
    private final JButton selectButton = new JButton("Select");

    private final DefaultListModel<String> selectedComponentsModel = new DefaultListModel<>();
    private final JList<String> selectedComponentsList = new JList<>(selectedComponentsModel);

    private final JButton downloadButton = new JButton("Download");
    private final JButton cancelButton = new JButton("Cancel");

    private final Map<String, ComponentCoordinate.Simple> selectedVersions = new HashMap<>();

    private final JProgressBar loadingBar = new JProgressBar();

    public ComponentSearchView(Collection<MetaRepository> repositories,
                               Map<String, ReconcilableGameComponent> existingComponents,
                               Function<List<ComponentCoordinate.Simple>, CompletableFuture<Void>> componentConsumer) {
        this.repositories = repositories;
        this.existingComponents = existingComponents;
        this.componentConsumer = componentConsumer;

        setLayout(new BorderLayout(10, 10));

        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(loadingBar, BorderLayout.NORTH);
        northPanel.add(searchPanel, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.4);

        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new ComponentSearchEntryRenderer());
        JScrollPane resultScroll = new JScrollPane(resultList);
        splitPane.setLeftComponent(resultScroll);

        JPanel detailPanel = new JPanel(new BorderLayout(5, 5));
        descriptionArea.setEditable(false);
        descriptionArea.setContentType("text/html");
        detailPanel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);

        JPanel versionPanel = new JPanel(new BorderLayout(5, 5));
        versionPanel.add(versionDropdown, BorderLayout.CENTER);
        versionPanel.add(selectButton, BorderLayout.EAST);
        detailPanel.add(versionPanel, BorderLayout.SOUTH);

        splitPane.setRightComponent(detailPanel);
        add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.add(new JScrollPane(selectedComponentsList), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(downloadButton);
        buttonPanel.add(cancelButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        wireEvents();

        this.performSearch();
    }

    private void wireEvents() {
        searchField.addActionListener(e -> searchButton.doClick());

        searchButton.addActionListener(e -> this.performSearch());

        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                RepoResultPair selectedPair = resultList.getSelectedValue();
                if (selectedPair != null) {
                    showComponentDetail(selectedPair);
                }
            }
        });

        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = resultList.locationToIndex(e.getPoint());
                    if (index != -1 && index == resultList.getSelectedIndex()) {
                        selectButton.doClick();
                    }
                }
            }
        });

        selectButton.addActionListener(e -> {
            var pair = resultList.getSelectedValue();
            if (pair != null && versionDropdown.getSelectedItem() != null) {
                GameComponent version = (GameComponent) versionDropdown.getSelectedItem();
                var result = pair.result();
                String label = result.metaInfo().name() + " - " + version;
                if (!selectedVersions.containsKey(result.uid())) {
                    selectedComponentsModel.addElement(label);
                    selectedVersions.put(result.uid(), new ComponentCoordinate.Simple(version));
                } else {
                    for (int i = 0; i < selectedComponentsModel.size(); i++) {
                        String item = selectedComponentsModel.get(i);
                        if (item.startsWith(result.metaInfo().name() + " -")) {
                            selectedComponentsModel.remove(i);
                            i--; // adjust index after removal
                        }
                    }
                    selectedVersions.remove(result.uid());
                }
            }
        });

        cancelButton.addActionListener(e -> this.dispose());
        downloadButton.addActionListener(ev -> {
            this.setVisible(false);
            this.componentConsumer.apply(List.copyOf(selectedVersions.values())).whenCompleteAsync((v, t) -> {
                if (t != null) {
                    LOGGER.error("Error downloading mods", t);
                }
                this.dispose();
            }, SwingUtilities::invokeLater);
        });
    }

    private void performSearch() {
        String queryText = searchField.getText().trim();
        ComponentSearchQuery query = ComponentSearchQuery.builder()
                .name(queryText.isEmpty() ? null : queryText)
                .currentComponents(existingComponents)
                .build();

        resultListModel.clear();
        descriptionArea.setText("");
        versionDropdown.removeAllItems();

        List<CompletableFuture<ComponentSearchResults>> futures = repositories.stream()
                .map(repo -> repo.search(query))
                .toList();

        loadingBar.setVisible(true);
        searchField.setEnabled(false);
        searchButton.setEnabled(false);
        CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .whenCompleteAsync((c, t) -> {
                    loadingBar.setVisible(false);
                    searchField.setEnabled(true);
                    searchButton.setEnabled(true);
                }, SwingUtilities::invokeLater)
                .thenApply(v -> {
                    List<RepoResultPair> results = new ArrayList<>();
                    Iterator<MetaRepository> repoIt = repositories.iterator();

                    for (CompletableFuture<ComponentSearchResults> future : futures) {
                        ComponentSearchResults res = future.join();
                        var repo = repoIt.next(); // Or use a `.getName()` if you have one
                        for (var r : res.results()) {
                            if (!r.metaInfo().isUserInstallable()) {
                                continue;
                            }
                            results.add(new RepoResultPair(repo, r));
                        }
                    }

                    return results;
                })
                .thenAccept(resultList -> SwingUtilities.invokeLater(() -> {
                    resultListModel.clear();
                    for (RepoResultPair pair : resultList) {
                        resultListModel.addElement(pair);
                    }
                }));
    }

    private void showComponentDetail(RepoResultPair pair) {
        if (pair == null) return;

        var result = pair.result();

        pair.result().metaInfo().description().thenAcceptAsync(description -> {
            descriptionArea.setText(description);
            descriptionArea.setCaretPosition(0);
        }, SwingUtilities::invokeLater);

        versionDropdown.removeAllItems();
        versionDropdown.setEnabled(false);
        loadingBar.setVisible(true);

        ComponentSearchQuery query = ComponentSearchQuery.builder()
                .name(result.metaInfo().name())
                .currentComponents(existingComponents)
                .build();

        pair.repository().getKnownVersions(result.uid(), query).thenAccept(components -> {
            SwingUtilities.invokeLater(() -> {
                versionDropdown.removeAllItems();
                for (GameComponent component : components) {
                    versionDropdown.addItem(component);
                }
                versionDropdown.setEnabled(true);
                loadingBar.setVisible(false);
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                versionDropdown.removeAllItems();
                JOptionPane.showMessageDialog(null, "There was an error loading versions: " + ex);
                versionDropdown.setEnabled(false);
                loadingBar.setVisible(false);
            });
            ex.printStackTrace();
            return null;
        });
    }

    private class ComponentSearchEntryRenderer extends JPanel implements ListCellRenderer<RepoResultPair> {
        private static final int ICON_SIZE = 48;

        private final JLabel iconLabel = new JLabel();
        private final JTextArea titleLabel = new JTextArea();
        private final JTextArea summaryLabel = new JTextArea();
        private final JLabel repoLabel = new JLabel();

        private final JPanel textPanel;
        private final Font normalTitleFont, selectedTitleFont;

        public ComponentSearchEntryRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            iconLabel.setHorizontalAlignment(JLabel.CENTER);
            iconLabel.setVerticalAlignment(JLabel.CENTER);
            iconLabel.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));

            textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            titleLabel.setOpaque(false);
            titleLabel.setEditable(false);
            titleLabel.setLineWrap(true);
            titleLabel.setWrapStyleWord(true);

            summaryLabel.setOpaque(false);
            summaryLabel.setEditable(false);
            summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, 12f));
            summaryLabel.setLineWrap(true);
            summaryLabel.setWrapStyleWord(true);

            repoLabel.setFont(repoLabel.getFont().deriveFont(Font.ITALIC, 11f));
            repoLabel.setForeground(Color.GRAY);

            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            repoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            textPanel.add(titleLabel);
            textPanel.add(summaryLabel);
            textPanel.add(repoLabel);

            styleRepoLabel(repoLabel);

            add(iconLabel, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);

            normalTitleFont = titleLabel.getFont().deriveFont(Font.BOLD);
            selectedTitleFont = normalTitleFont.deriveFont(Font.BOLD | Font.ITALIC);
        }

        private void styleRepoLabel(JLabel label) {
            label.setOpaque(true);
            label.setBackground(new Color(60, 60, 60)); // light gray
            label.setForeground(Color.WHITE);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(180, 180, 180), 1, true),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6) // padding
            ));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RepoResultPair> list,
                                                      RepoResultPair value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            ComponentSearchResults.Result result = value.result();
            ComponentMetaInfo info = result.metaInfo();

            boolean isSelectedForInstall = selectedVersions.containsKey(result.uid());

            titleLabel.setFont(isSelectedForInstall ? selectedTitleFont : normalTitleFont);
            titleLabel.setText(info.name());

            // Placeholder summary (replace with real description field when available)
            int labelWidth = list.getWidth() - ICON_SIZE - 20 - 20;

            summaryLabel.setText(SwingHelpers.ellipsize(summaryLabel.getFontMetrics(summaryLabel.getFont()), info.summary(), labelWidth));

            repoLabel.setText(value.repository().toString());

            if (info.logoUrl() != null) {
                var future = modIcons.computeIfAbsent(info.logoUrl(), url -> CompletableFuture.supplyAsync(() -> IconUtil.loadIconFromURL(url, ICON_SIZE)).whenCompleteAsync((c, t) -> ComponentSearchView.this.resultList.repaint(), SwingUtilities::invokeLater));
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

            setOpaque(true);

            int availableWidth = list.getWidth() - ICON_SIZE - 30; // 30 for spacing/margin
            if (availableWidth > 0) {
                Dimension constraint = new Dimension(availableWidth, Short.MAX_VALUE);
                titleLabel.setSize(constraint);
                summaryLabel.setSize(constraint);

                titleLabel.setPreferredSize(titleLabel.getPreferredSize());
                summaryLabel.setPreferredSize(summaryLabel.getPreferredSize());
            }

            return this;
        }
    }
}
