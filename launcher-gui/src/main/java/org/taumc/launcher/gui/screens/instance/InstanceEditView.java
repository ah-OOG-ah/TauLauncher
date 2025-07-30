package org.taumc.launcher.gui.screens.instance;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.qsettings.Settings;
import org.taumc.launcher.core.reconciler.ReconciliationHelpers;
import org.taumc.launcher.core.reconciler.intervention.ConsoleInterventionHandler;
import org.taumc.launcher.core.reconciler.tree.ComponentTreeNode;
import org.taumc.launcher.gui.SwingHelpers;
import org.taumc.launcher.gui.UIPaths;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.gui.components.MultiSectionFrame;
import org.taumc.launcher.gui.icon.IconRegistry;
import org.taumc.launcher.gui.launch.LaunchHandler;
import org.taumc.launcher.gui.launch.LogViewFrame;
import org.taumc.launcher.gui.launch.ProgressDialog;
import org.taumc.launcher.gui.screens.home.HomeView;
import org.taumc.launcher.gui.util.ExceptionUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class InstanceEditView extends MultiSectionFrame {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceEditView.class);

    public static final Map<String, InstanceEditView> OPEN_EDIT_VIEWS = new HashMap<>();

    private final HomeView owner;
    private final String instance;
    private final Path instancePath;
    private final Path mmcPackJson, instanceCfgPath;
    private final DefaultListModel<ReconcilableGameComponent> componentList = new DefaultListModel<>();
    private final Settings instanceCfg;

    private final Map<ComponentCoordinate.Simple, CompletableFuture<String>> componentFutureMap = new HashMap<>();

    private final CompletableFuture<MetadataService> metadataService;

    private final ProgressDialog progressDialog;

    private record Page(String name, Function<InstanceEditView, JPanel> builder) {}

    private InstanceEditView(HomeView owner, String instance) {
        super();
        this.setTitle(instance);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.owner = owner;
        this.instance = instance;
        this.instancePath = UIPaths.INSTANCES_FOLDER.resolve(this.instance);
        this.mmcPackJson = this.instancePath.resolve("mmc-pack.json");
        this.instanceCfgPath = this.instancePath.resolve("instance.cfg");
        this.instanceCfg = new Settings();

        var icon = IconRegistry.findIcon(this.instanceCfg.getValue("iconKey").orElse("default_instance"),this.instancePath);

        this.setIconImage(icon.getImage());

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

        this.progressDialog = new ProgressDialog(this);

        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Add pages
        var pages = List.of(
                new Page("Components", InstanceEditView::createComponentsPanel),
                new Page("Memory & JVM", InstanceEditView::createMemoryPanel),
                new Page("Logs", InstanceEditView::mountLogsPanel)
        );
        pages.forEach(page -> this.addPage(page.name, () -> page.builder.apply(this)));

        OPEN_EDIT_VIEWS.put(instance, this);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent ev) {
                InstanceEditView.this.saveCurrentConfig();
                InstanceEditView.this.metadataService.join().close();
                OPEN_EDIT_VIEWS.remove(instance);
                owner.refreshSidebar();
            }
        });

        try {
            this.readCurrentConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.setVisible(true);
    }

    public static boolean isLocked(String instance) {
        return OPEN_EDIT_VIEWS.containsKey(instance);
    }

    public static InstanceEditView createOrShow(HomeView owner, String instance) {
        var view = OPEN_EDIT_VIEWS.get(instance);
        if (view != null) {
            view.toFront();
            return view;
        } else {
            return new InstanceEditView(owner, instance);
        }
    }

    private void readCurrentConfig() throws IOException {
        var mapper = new ObjectMapper();
        var mmcPack = mapper.readValue(this.mmcPackJson.toFile(), MMCPack.class);
        this.componentList.clear();
        this.componentList.addAll(mmcPack.components().stream().map(coord -> this.metadataService.thenCompose(s -> s.getComponent(coord)).join()).toList());

        this.instanceCfg.clear();
        if (Files.exists(this.instanceCfgPath)) {
            this.instanceCfg.readFrom(this.instanceCfgPath);
        }
    }

    private void saveCurrentConfig() {
        var mapper = new ObjectMapper();
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        prettyPrinter.indentObjectsWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        var mmcPack = new MMCPack(1, SwingHelpers.immutableListOf(componentList).stream().map(ComponentCoordinate.Simple::new).toList());
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

        JList<ReconcilableGameComponent> components = new JList<>(componentList) {
            final String[] messageLines = {
                    "No components are currently installed.",
                    "Add components using the + button on the right."
            };

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getModel().getSize() == 0) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    // Use the current UI font and foreground color
                    Font font = UIManager.getFont("Label.font").deriveFont(Font.BOLD, 18f);
                    Color fg = UIManager.getColor("Label.foreground");
                    g2d.setFont(font);
                    g2d.setColor(fg);
                    FontMetrics fm = g2d.getFontMetrics();

                    int lineHeight = fm.getHeight();
                    int extraHeight = 20;
                    int totalHeight = lineHeight * messageLines.length + extraHeight;
                    int maxWidth = 0;
                    for (String line : messageLines) {
                        maxWidth = Math.max(maxWidth, fm.stringWidth(line));
                    }

                    int boxWidth = maxWidth + 40;
                    int boxHeight = totalHeight;
                    int boxX = (getWidth() - boxWidth) / 2;
                    int boxY = (getHeight() - boxHeight) / 2;

                    // Draw background using FlatLaf-style color
                    Color bg = UIManager.getColor("TextField.background");
                    Color border = UIManager.getColor("Component.borderColor");

                    g2d.setColor(bg != null ? bg : new Color(240, 240, 240));
                    g2d.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 16, 16);
                    g2d.setColor(border != null ? border : Color.GRAY);
                    g2d.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 16, 16);

                    // Draw text
                    g2d.setColor(fg);
                    int y = boxY + extraHeight / 2 + fm.getAscent();
                    for (String line : messageLines) {
                        int x = (getWidth() - fm.stringWidth(line)) / 2;
                        g2d.drawString(line, x, y);
                        y += lineHeight;
                    }

                    g2d.dispose();
                }
            }
        };

        components.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value instanceof ReconcilableGameComponent component) {
                    setText(component.name() + " " + component.friendlyVersion());
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
        removeButton.addActionListener(ev -> {
            var component = components.getSelectedValue();
            if (component != null) {
                var oldRoot = new ComponentTreeNode(null, SwingHelpers.immutableListOf(componentList).stream().map(ComponentTreeNode::new).toList());
                var newRoot = oldRoot.clone();
                newRoot.children().removeIf(c -> component.equals(c.getComponent()));
                this.updateInstance(oldRoot, newRoot, () -> componentList.removeElement(component));
            }
        });

        addButton.addActionListener(ev -> {
            var metaService = this.metadataService.join();
            var oldRoot = new ComponentTreeNode(null, SwingHelpers.immutableListOf(componentList).stream().map(ComponentTreeNode::new).toList());
            var view = new ComponentSearchView(metaService.getRepositories(), oldRoot.buildIndex(), newComponents -> {
                var newRoot = oldRoot.clone();
                var reconcilableComponents = newComponents.stream().map(c -> metaService.getComponent(c).join()).toList();
                reconcilableComponents.forEach(r -> newRoot.addChild(new ComponentTreeNode(r)));
                return this.updateInstance(oldRoot, newRoot, () -> componentList.addAll(reconcilableComponents));
            });
            view.setVisible(true);
        });
        panel.add(components, BorderLayout.CENTER);

        return panel;
    }

    private CompletableFuture<Void> updateInstance(ComponentTreeNode oldRoot, ComponentTreeNode newRoot, Runnable applyUpdate) {
        return CompletableFuture.runAsync(() -> {
            try {
                ReconciliationHelpers.migrateInstance(LaunchHandler.computeMinecraftFolder(instancePath),
                        oldRoot,
                        newRoot,
                        this.metadataService.join(),
                        this.progressDialog,
                        new ConsoleInterventionHandler());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenCompleteAsync((c, e) -> {
            if (e != null) {
                JOptionPane.showMessageDialog(this, e.toString(), "Error updating instance", JOptionPane.ERROR_MESSAGE);
                LOGGER.error("Error updating instance", e);
            } else {
                applyUpdate.run();
                saveCurrentConfig();
                refreshCurrentPanel();
            }
        }, SwingUtilities::invokeLater);
    }

    private JPanel createMemoryPanel() {
        return new MemorySettingsPanel(this.instanceCfg);
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
