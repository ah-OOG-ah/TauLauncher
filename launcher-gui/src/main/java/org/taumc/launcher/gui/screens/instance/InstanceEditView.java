package org.taumc.launcher.gui.screens.instance;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.json.HTTPMetaRepository;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.meta.json.PatchesFolderMetaRepository;
import org.taumc.launcher.core.qsettings.Settings;
import org.taumc.launcher.gui.Main;
import org.taumc.launcher.gui.UIPaths;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.gui.launch.LaunchHandler;
import org.taumc.launcher.gui.launch.LogViewFrame;
import org.taumc.launcher.gui.screens.home.HomeView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.IntStream;

public class InstanceEditView extends JFrame {
    private static final Map<String, Function<InstanceEditView, JPanel>> PAGES = new LinkedHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceEditView.class);

    public static final Map<String, InstanceEditView> OPEN_EDIT_VIEWS = new HashMap<>();

    static {
        PAGES.put("Components", InstanceEditView::createComponentsPanel);
        PAGES.put("Memory & JVM", InstanceEditView::createMemoryPanel);
        PAGES.put("Mods", InstanceEditView::createModsPanel);
        PAGES.put("Logs", InstanceEditView::mountLogsPanel);
    }

    private final HomeView owner;
    private final String instance;
    private final Path instancePath;
    private final Path mmcPackJson, instanceCfgPath;
    private final DefaultListModel<MMCPack.Component> componentList = new DefaultListModel<>();
    private final Settings instanceCfg;
    private final JList<String> sidebar;
    private final JPanel cardPanel;
    private final Map<String, JPanel> panels;

    private final Map<MMCPack.Component, CompletableFuture<String>> componentFutureMap = new HashMap<>();

    private final CompletableFuture<MetadataService> metadataService;

    private InstanceEditView(HomeView owner, String instance) {
        super(instance);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.owner = owner;
        this.instance = instance;
        this.instancePath = UIPaths.INSTANCES_FOLDER.resolve(this.instance);
        this.mmcPackJson = this.instancePath.resolve("mmc-pack.json");
        this.instanceCfgPath = this.instancePath.resolve("instance.cfg");
        this.instanceCfg = new Settings();
        try {
            this.readCurrentConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Build a metadata service for the instance
        this.metadataService = CompletableFuture.supplyAsync(() -> {
            var service = new MetadataService();
            try {
                LaunchHandler.configureMetadataService(service, this.instancePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return service;
        });

        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setSize(800, 600);

        // Sidebar: list of settings pages
        this.sidebar = new JList<>(PAGES.keySet().toArray(new String[0]));
        sidebar.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebar.setSelectedIndex(0);

        JScrollPane sidebarScroll = new JScrollPane(sidebar);
        sidebarScroll.setPreferredSize(new Dimension(150, 0));

        // Main panel with CardLayout
        this.cardPanel = new JPanel(new CardLayout());

        this.panels = new HashMap<>();
        PAGES.forEach((name, panelSupplier) -> this.panels.put(name, panelSupplier.apply(this)));
        panels.forEach((name, panel) -> cardPanel.add(panel, name));

        // Change card on selection
        sidebar.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = sidebar.getSelectedValue();
                CardLayout cl = (CardLayout) cardPanel.getLayout();
                cl.show(cardPanel, selected);
            }
        });

        // Split layout
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarScroll, cardPanel);
        splitPane.setDividerLocation(150);
        splitPane.setResizeWeight(0);

        this.add(splitPane);
        this.setLocationRelativeTo(null);
        this.setVisible(true);
        OPEN_EDIT_VIEWS.put(instance, this);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent ev) {
                InstanceEditView.this.saveCurrentConfig();
                InstanceEditView.this.metadataService.join().close();
                OPEN_EDIT_VIEWS.remove(instance);
                panels.values().forEach(p -> p.getParent().remove(p));
                owner.refreshSidebar();
            }
        });
    }

    public static boolean isLocked(String instance) {
        return OPEN_EDIT_VIEWS.containsKey(instance);
    }

    public static void createOrShow(HomeView owner, String instance) {
        var view = OPEN_EDIT_VIEWS.get(instance);
        if (view != null) {
            view.toFront();
        } else {
            new InstanceEditView(owner, instance);
        }
    }

    private void readCurrentConfig() throws IOException {
        var mapper = new ObjectMapper();
        var mmcPack = mapper.readValue(this.mmcPackJson.toFile(), MMCPack.class);
        this.componentList.clear();
        this.componentList.addAll(mmcPack.components());

        this.instanceCfg.clear();
        if (Files.exists(this.instanceCfgPath)) {
            this.instanceCfg.readFrom(this.instanceCfgPath);
        }
    }

    private List<MMCPack.Component> getCurrentComponents() {
        return IntStream.range(0, this.componentList.size()).mapToObj(this.componentList::getElementAt).toList();
    }

    private void saveCurrentConfig() {
        var mapper = new ObjectMapper();
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        prettyPrinter.indentObjectsWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        var mmcPack = new MMCPack(1, this.getCurrentComponents());
        try {
            mapper.writer(prettyPrinter).writeValue(this.mmcPackJson.toFile(), mmcPack);
        } catch (IOException e) {
            LOGGER.error("Error saving mmc-pack.json", e);
        }

        try {
            this.instanceCfg.writeTo(this.instanceCfgPath);
        } catch (IOException e) {
            LOGGER.error("Error saving instance.cfg", e);
        }
    }

    private void refreshCurrentPanel() {
        String page = this.sidebar.getSelectedValue();
        JPanel prev = this.panels.remove(page);
        if (prev != null) {
            this.cardPanel.remove(prev);
        }
        JPanel newPanel = PAGES.get(page).apply(this);
        this.cardPanel.add(newPanel, page);
        ((CardLayout)this.cardPanel.getLayout()).show(this.cardPanel, page);
    }

    private JPanel createComponentsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JButton addButton = new JButton(FontIcon.of(FontAwesomeSolid.PLUS, 32, Color.LIGHT_GRAY));
        sidebar.add(addButton);

        JButton removeButton = new JButton(FontIcon.of(FontAwesomeSolid.MINUS, 32, Color.LIGHT_GRAY));
        removeButton.setEnabled(false);
        sidebar.add(removeButton);

        JButton editButton = new JButton(FontIcon.of(FontAwesomeSolid.EDIT, 32, Color.LIGHT_GRAY));
        editButton.setEnabled(false);
        sidebar.add(editButton);

        panel.add(sidebar, BorderLayout.EAST);

        JList<MMCPack.Component> components = new JList<>(componentList);

        components.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value instanceof MMCPack.Component component) {
                    var future = componentFutureMap.computeIfAbsent(component, coord -> metadataService.thenApplyAsync(meta -> {
                        return meta.getComponent(coord.uid(), coord.version());
                    }).handle((c, t) -> {
                        if (c != null) {
                            return c.name() + " " + c.version();
                        } else {
                            return coord.uid() + " " + coord.version();
                        }
                    }).whenCompleteAsync((s, t) -> components.repaint(), SwingUtilities::invokeLater));
                    setText(future.getNow(component.uid() + " " + component.version()));
                }

                return this;
            }
        });
        components.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        components.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                boolean isSelected = components.getSelectedValue() != null;
                removeButton.setEnabled(isSelected);
                editButton.setEnabled(isSelected);
            }
        });
        removeButton.addActionListener(e -> {
            if (components.getSelectedValue() != null) {
                componentList.removeElement(components.getSelectedValue());
                saveCurrentConfig();
            }
        });
        editButton.addActionListener(e -> {
            var current = components.getSelectedValue();
            new NewComponentDialog(this, this.getCurrentComponents(), coordinate -> {
                componentList.removeElement(current);
                componentList.addElement(new MMCPack.Component(coordinate.uid(), coordinate.version()));
                saveCurrentConfig();
            }, current);
        });
        addButton.addActionListener(e -> {
            new NewComponentDialog(this, this.getCurrentComponents(), coordinate -> {
                componentList.addElement(new MMCPack.Component(coordinate.uid(), coordinate.version()));
                saveCurrentConfig();
                this.refreshCurrentPanel();
            });
        });
        panel.add(components, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createMemoryPanel() {
        return new MemorySettingsPanel(this.instanceCfg);
    }

    private JPanel createModsPanel() {
        return new ModManagerPanel(instancePath, this, this.componentList);
    }

    private JPanel mountLogsPanel() {
        return LogViewFrame.forInstance(this.instance);
    }

    private static JPanel createLabelPanel(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(text, SwingConstants.CENTER), BorderLayout.CENTER);
        return panel;
    }
}
