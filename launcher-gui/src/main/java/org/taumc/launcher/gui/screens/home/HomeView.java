package org.taumc.launcher.gui.screens.home;

import com.formdev.flatlaf.FlatClientProperties;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.auth.Account;
import org.taumc.launcher.core.auth.microsoft.MicrosoftAccount;
import org.taumc.launcher.core.importer.InstanceImporter;
import org.taumc.launcher.core.mods.ProjectType;
import org.taumc.launcher.core.mods.curseforge.CurseForgeAPI;
import org.taumc.launcher.core.mods.curseforge.CurseForgeInstanceCreator;
import org.taumc.launcher.core.mods.curseforge.File;
import org.taumc.launcher.core.mods.modpacksch.DownloadableModpackVersion;
import org.taumc.launcher.core.mods.modpacksch.ModpacksCHInstanceCreator;
import org.taumc.launcher.core.nio.PathUtils;
import org.taumc.launcher.gui.Main;
import org.taumc.launcher.gui.SwingHelpers;
import org.taumc.launcher.gui.components.FileChooser;
import org.taumc.launcher.gui.components.TauLauncherFrame;
import org.taumc.launcher.gui.components.WrapLayout;
import org.taumc.launcher.gui.icon.IconRegistry;
import org.taumc.launcher.gui.launch.LaunchHandler;
import org.taumc.launcher.gui.launch.ProgressDialog;
import org.taumc.launcher.gui.screens.curseforge.ManualDownloadDialog;
import org.taumc.launcher.gui.screens.instance.InstanceEditView;
import org.taumc.launcher.gui.screens.mods.AddModsView;
import org.taumc.launcher.gui.screens.settings.GlobalSettingsView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class HomeView extends TauLauncherFrame {
    private static final Logger LOGGER = LoggerFactory.getLogger(HomeView.class);

    private static final Account FAKE_ACCOUNT = new Account() {
        @Override
        public String username() {
            return "";
        }

        @Override
        public String accessToken() {
            return "";
        }

        @Override
        public String toString() {
            return "Add Account...";
        }

        @Override
        public String type() {
            return "unknown";
        }
    };
    private final ButtonGroup instanceButtonGroup;
    private final JPanel instanceGridPanel;
    private final JPanel sidebar;
    private final HomeModel model;

    private final Map<String, LaunchHandler> runningInstances = new HashMap<>();

    private final ProgressDialog progressDialog;

    public HomeView(HomeModel model) {
        this.model = model;
        this.setTitle("TauLauncher " + System.getProperty("taulauncher.version"));
        this.setIconImage(IconRegistry.loadAndScaleImageIcon(HomeView.class.getResourceAsStream("/taulauncher/icons/tau_logo.png"), 64).getImage());
        this.instanceButtonGroup = new ButtonGroup();
        var buttonLayout = new WrapLayout(FlowLayout.LEFT);
        buttonLayout.setAlignOnBaseline(false);
        this.instanceGridPanel = new JPanel(buttonLayout);
        this.instanceGridPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (model.getSelectedInstance() != null && SwingUtilities.getDeepestComponentAt(instanceGridPanel, e.getX(), e.getY()) == instanceGridPanel) {
                    model.setSelectedInstance(null);
                    refreshInstanceButtons();
                    refreshSidebar();
                }
            }
        });
        // Sidebar with add/remove controls
        this.sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        this.progressDialog = new ProgressDialog(this);

        // Container with grid on left, sidebar on right
        JSplitPane container = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(instanceGridPanel),
                sidebar
        );
        container.setDividerLocation(650);
        container.setResizeWeight(1.0);

        // Panel to go above split pane
        JButton addInstanceButton = new JButton("Add Instance");
        addInstanceButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        addInstanceButton.addActionListener(e -> this.showCreateInstanceDialog());
        JButton importButton = new JButton("Import Instance");
        importButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        importButton.addActionListener(e -> this.showImportInstanceDialog());
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
        topPanel.add(addInstanceButton);
        topPanel.add(importButton);

        JButton settingsButton = new JButton("Settings");
        settingsButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        settingsButton.addActionListener(e -> new GlobalSettingsView());

        //accountComboBox.setMaximumSize(new Dimension(50, accountComboBox.getPreferredSize().height));

        topPanel.add(Box.createHorizontalGlue());
        topPanel.add(settingsButton);

        Container contentPane = this.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(topPanel, BorderLayout.NORTH);
        contentPane.add(container, BorderLayout.CENTER);

        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Update initial state
        this.refreshInstanceButtons();
        this.refreshSidebar();

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    Main.ACCOUNTS.saveToDisk();
                } catch (IOException ex) {
                    LOGGER.error("Error saving", ex);
                }
            }
        });
    }

    private void showCreateInstanceDialog() {
        String name = JOptionPane.showInputDialog(null, "Enter instance name:");
        if (name == null) {
            return;
        }
        try {
            model.createInstance(name);
            model.setSelectedInstance(name);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            this.refreshInstanceButtons();
            this.refreshSidebar();
        }
        InstanceEditView.createOrShow(this, name);
    }

    private void showImportInstanceDialog() {
        Object[] options = {
                "Import from Mod Site",
                "Import from Local File",
                "Cancel"
        };

        int choice = JOptionPane.showOptionDialog(
                null,
                "Choose how you want to import the instance:",
                "Import Instance",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        var future = switch (choice) {
            case 0 -> showModHostingImportInstanceDialog();
            case 1 -> showLocalFileImportInstanceDialog();
            default -> CompletableFuture.completedFuture(null);
        };

        future.whenCompleteAsync((c, t) -> {
            IconRegistry.resetCache();
            if (t != null) {
                Throwable realCause;
                if (t instanceof CompletionException && t.getCause() != null) {
                    realCause = t.getCause();
                } else {
                    realCause = t;
                }
                LOGGER.error("Error importing", realCause);
                JOptionPane.showMessageDialog(null, realCause.toString(), "Error", JOptionPane.ERROR_MESSAGE);
            }
            model.updateInstanceFolders();
            this.refreshInstanceButtons();
        }, SwingUtilities::invokeLater);
    }

    private CompletableFuture<Void> showModHostingImportInstanceDialog() {
        var future = new CompletableFuture<Void>();
        var addModsView = new AddModsView(this, List.of(), ProjectType.MODPACK, pack -> {
            CompletableFuture<Void> innerFuture;
            if (!pack.isEmpty()) {
                var thePack = pack.getFirst();
                if (thePack instanceof File file) {
                    String baseInstanceName = file.displayName();
                    if (baseInstanceName.isBlank()) {
                        baseInstanceName = "CurseForge Modpack";
                    }
                    var path = model.getInstancePath(PathUtils.findNonexistentName(baseInstanceName, s -> Files.exists(model.getInstancePath(s))));
                    innerFuture = CompletableFuture.runAsync(() -> {
                        var creator = new CurseForgeInstanceCreator(CurseForgeAPI.INSTANCE, new ManualDownloadDialog());
                        try {
                            creator.createInstance(path, file.modId(), file.id(), this.progressDialog);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).whenCompleteAsync((c, t) -> {
                        if (t != null) {
                            try {
                                PathUtils.deleteRecursively(path);
                            } catch (IOException ignored) {
                            }
                        }
                    });
                } else if (thePack instanceof DownloadableModpackVersion ftbModpackVersion) {
                    String baseInstanceName = ftbModpackVersion.modpackName();
                    if (baseInstanceName.isBlank()) {
                        baseInstanceName = "FTB Modpack";
                    }
                    var path = model.getInstancePath(PathUtils.findNonexistentName(baseInstanceName, s -> Files.exists(model.getInstancePath(s))));
                    innerFuture = CompletableFuture.runAsync(() -> {
                        var creator = new ModpacksCHInstanceCreator(new ManualDownloadDialog());
                        try {
                            creator.createInstance(path, ftbModpackVersion, this.progressDialog);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).whenCompleteAsync((c, t) -> {
                        if (t != null) {
                            try {
                                PathUtils.deleteRecursively(path);
                            } catch (IOException ignored) {
                            }
                        }
                    });
                } else {
                    JOptionPane.showMessageDialog(this, "Cannot import pack type " + thePack.getClass().getName() + " right now", "Error", JOptionPane.ERROR_MESSAGE);
                    innerFuture = CompletableFuture.completedFuture(null);
                }
            } else {
                innerFuture = CompletableFuture.completedFuture(null);
            }
            return innerFuture.whenComplete((c, t) -> {
                if (t != null) {
                    future.completeExceptionally(t);
                } else {
                    future.complete(c);
                }
            });
        });
        addModsView.setAllowMultipleSelection(false);
        return future;
    }

    private CompletableFuture<Void> showLocalFileImportInstanceDialog() {
        FileChooser fileChooser = FileChooser.getInstance();
        var selectedFiles = fileChooser.showFilePicker();

        if (!selectedFiles.isEmpty()) {
            var path = selectedFiles.getFirst().toPath();

            return CompletableFuture.runAsync(() -> {
                String instanceName = path.getFileName().toString();
                int lastDot = instanceName.lastIndexOf('.');
                if (lastDot != -1) {
                    instanceName = instanceName.substring(0, lastDot);
                }
                String adjustedInstanceName = instanceName;
                int i = 1;
                while (model.getInstanceFolders().contains(adjustedInstanceName)) {
                    adjustedInstanceName = instanceName + "(" + i++ + ")";
                }
                try (FileSystem zipfs = FileSystems.newFileSystem(path, Map.of("create", "false"))) {
                    var importer = new InstanceImporter();
                    importer.setCurseForgeManualDownloadService(new ManualDownloadDialog());
                    importer.importInstance(zipfs.getRootDirectories().iterator().next(), model.getInstancePath(adjustedInstanceName), this.progressDialog);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    public void refreshInstanceButtons() {
        this.instanceGridPanel.removeAll();
        this.instanceButtonGroup.clearSelection();

        String selected = model.getSelectedInstance();

        for (String name : model.getInstanceFolders()) {
            var settings = model.getSettings(name);
            var icon = IconRegistry.findIcon(settings.getValue("iconKey").orElse("default_instance"), model.getInstancePath(name));
            ToggleIconButton toggle = new ToggleIconButton(icon, name);
            toggle.setName(name);

            // Select toggle if matches model.selectedId
            toggle.setSelected(name.equals(selected));

            toggle.addActionListener(e -> {
                if (toggle.isSelected()) {
                    model.setSelectedInstance(name);
                } else if (name.equals(selected)) {
                    model.setSelectedInstance(null);
                }
                this.refreshSidebar();
            });

            this.instanceButtonGroup.add(toggle);
            this.instanceGridPanel.add(toggle);

            if (name.equals(model.getSelectedInstance())) {
                this.instanceButtonGroup.setSelected(toggle.getModel(), true);
            }
        }

        instanceGridPanel.revalidate();
        instanceGridPanel.repaint();
    }

    private static JButton makeSidebarButton(String text) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        return button;
    }

    public void refreshSidebar() {
        sidebar.removeAll();

        String selectedInstance = model.getSelectedInstance();
        if (selectedInstance != null) {
            JLabel image = new JLabel(new ImageIcon(""));  // Empty icon path in your original
            image.setPreferredSize(new Dimension(50, 50));
            sidebar.add(image);

            JLabel label = new JLabel(selectedInstance);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            // To fill full width, if sidebar uses BoxLayout.Y_AXIS:
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
            sidebar.add(label);

            SwingHelpers.addSeparator(sidebar, SwingConstants.HORIZONTAL);

            JButton launchButton = makeSidebarButton("Launch");
            JButton killButton = makeSidebarButton("Kill");

            runningInstances.values().removeIf(h -> !h.isRunning());

            LaunchHandler existingLaunchHandler = runningInstances.get(selectedInstance);

            launchButton.setEnabled(existingLaunchHandler == null);
            killButton.setEnabled(existingLaunchHandler != null && existingLaunchHandler.isRunning());

            launchButton.addActionListener(e -> {
                Account currentAccount = Main.ACCOUNTS.getPreferredAccount();
                if (currentAccount == null) {
                    JOptionPane.showMessageDialog(null, "You must add an account before playing the game!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                var handler = new LaunchHandler(selectedInstance, this, currentAccount);
                runningInstances.put(selectedInstance, handler);
                handler.doLaunch().whenComplete((p, t) -> {
                    if (t != null) {
                        Throwable error = t;
                        if (error instanceof CompletionException c && c.getCause() != null) {
                            error = c.getCause();
                        }
                        JOptionPane.showMessageDialog(null, error.toString(), "Error launching game", JOptionPane.ERROR_MESSAGE);
                    }
                    SwingUtilities.invokeLater(this::refreshSidebar);
                    handler.exitFuture().whenCompleteAsync((p2, t2) -> {
                        if (p2 != null && p2.exitValue() != 0) {
                            var view = InstanceEditView.createOrShow(this, selectedInstance);
                            view.setCurrentPage("Logs");
                        }
                        this.refreshSidebar();
                    }, SwingUtilities::invokeLater);
                });
                refreshSidebar();
            });

            killButton.addActionListener(e -> {
                if (existingLaunchHandler == null) {
                    return;
                }
                int result = JOptionPane.showConfirmDialog(
                        this,
                        "Are you sure you want to kill this instance? You may lose data!",
                        "Confirm",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (result == JOptionPane.YES_OPTION) {
                    killButton.setEnabled(false);
                    existingLaunchHandler.terminate();
                }
            });

            sidebar.add(launchButton);
            sidebar.add(killButton);

            SwingHelpers.addSeparator(sidebar, SwingConstants.HORIZONTAL);

            JButton editButton = makeSidebarButton("Edit");
            editButton.addActionListener(ev -> {
                var view = InstanceEditView.createOrShow(this, selectedInstance);
                if (existingLaunchHandler != null) {
                    view.setCurrentPage("Logs");
                }
                refreshSidebar();
            });
            sidebar.add(editButton);

            JButton deleteButton = makeSidebarButton("Delete");
            deleteButton.setEnabled(!InstanceEditView.isLocked(selectedInstance));
            deleteButton.addActionListener(ev -> {
                int result = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete instance '%s'?".formatted(selectedInstance), "Confirm", JOptionPane.YES_NO_OPTION);
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
                try {
                    model.deleteInstance(selectedInstance);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(null, e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                refreshInstanceButtons();
                refreshSidebar();
            });
            sidebar.add(deleteButton);

            JButton renameButton = makeSidebarButton("Rename");
            renameButton.setEnabled(existingLaunchHandler == null && !InstanceEditView.isLocked(selectedInstance));
            renameButton.addActionListener(ev -> {
                String result = (String) JOptionPane.showInputDialog(
                        null,
                        "Enter new instance name:",
                        "Rename",
                        JOptionPane.PLAIN_MESSAGE,
                        null,      // no icon
                        null,      // no selection values (it's just a plain text box)
                        selectedInstance  // this sets the initial input value
                );
                if (result == null || result.equals(selectedInstance)) {
                    return;
                }
                try {
                    model.renameInstance(selectedInstance, result);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(null, e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
                }
                refreshInstanceButtons();
                refreshSidebar();
            });
            sidebar.add(deleteButton);
            sidebar.add(renameButton);

            if (Desktop.isDesktopSupported()) {
                JButton folderButton = makeSidebarButton("View Folder");
                folderButton.addActionListener(ev -> {
                    var filePath = model.getInstancePath(selectedInstance).toFile();
                    var t = new Thread(() -> {
                        try {
                            Desktop.getDesktop().open(filePath);
                        } catch (IOException e) {
                            LOGGER.error("Error opening folder", e);
                        }
                    });
                    t.setDaemon(true);
                    t.start();
                });
                sidebar.add(folderButton);
            }
        }


        sidebar.revalidate();
        sidebar.repaint();
    }
}
