package org.taumc.launcher.gui.screens.instance;

import org.taumc.launcher.gui.Main;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.Requirement;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NewComponentDialog extends JDialog {
    private final List<ComponentCoordinate.Simple> existingComponents;

    private static final Set<String> IGNORED_REQUIREMENTS = Set.of("org.lwjgl", "org.lwjgl3", "net.fabricmc.intermediary");

    public NewComponentDialog(Frame parent, List<ComponentCoordinate.Simple> existingComponents, Consumer<ComponentCoordinate> onAdd) {
        this(parent, existingComponents, onAdd, null);
    }

    public NewComponentDialog(Frame parent, List<ComponentCoordinate.Simple> existingComponents, Consumer<ComponentCoordinate> onAdd, ComponentCoordinate initialSelection) {
        super(parent, "Add Component", true);
        this.existingComponents = existingComponents;

        Map<String, List<String>> options = new HashMap<>();

        var componentMap = existingComponents.stream().collect(Collectors.toUnmodifiableMap(ComponentCoordinate.Simple::uid, Function.identity()));

        Predicate<Requirement> requirementsSatisfied = r -> r.isSatisfied(componentMap);
        // Compute the valid components to add
        for (String pkg : Main.METADATA.getKnownPackages()) {
            if (initialSelection == null && existingComponents.stream().anyMatch(c -> c.uid().equals(pkg))) {
                continue;
            }
            if (initialSelection != null && !initialSelection.uid().equals(pkg)) {
                continue;
            }
            boolean isTopLevel = Main.METADATA.getComponentMeta(pkg).join().isUserInstallable();
            if (!isTopLevel) {
                continue;
            }
            if (this.existingComponents.isEmpty() && !pkg.equals("net.minecraft")) {
                continue;
            }
            LinkedHashSet<String> versions = new LinkedHashSet<>();
            for (var version : Main.METADATA.getKnownVersions(pkg).join()) {
                if (version.requires() == null || version.requires().stream().filter(r -> !IGNORED_REQUIREMENTS.contains(r.uid())).allMatch(requirementsSatisfied)) {
                    versions.add(version.version());
                }
            }
            if (!versions.isEmpty()) {
                options.put(pkg, List.copyOf(versions));
            }
        }

        DefaultListModel<String> componentModel = new DefaultListModel<>();
        options.keySet().stream().sorted().forEach(componentModel::addElement);
        JList<String> componentList = new JList<>(componentModel);
        componentList.setCellRenderer(new ComponentCellRenderer());
        componentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane componentScroll = new JScrollPane(componentList);

        // Right list: versions (changes based on selection)
        DefaultListModel<String> versionModel = new DefaultListModel<>();
        JList<String> versionList = new JList<>(versionModel);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane versionScroll = new JScrollPane(versionList);

        // Link selection: when component is selected, update version list
        componentList.addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                String selectedComponent = componentList.getSelectedValue();
                versionModel.clear();
                if (selectedComponent != null) {
                    options.getOrDefault(selectedComponent, java.util.List.of())
                            .forEach(versionModel::addElement);
                }
            }
        });

        if (initialSelection != null && options.containsKey(initialSelection.uid())) {
            componentList.setSelectedValue(initialSelection.uid(), true);
            if (options.getOrDefault(initialSelection.uid(), List.of()).contains(initialSelection.version())) {
                versionList.setSelectedValue(initialSelection.version(), true);
            }
        }

        // Layout
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                componentScroll,
                versionScroll
        );
        splitPane.setResizeWeight(0.3);

        // Create a modal dialog with the custom panel
        this.getContentPane().add(splitPane, BorderLayout.CENTER);

        // Add OK and Cancel buttons
        JPanel buttons = new JPanel();
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");

        okBtn.addActionListener(e -> {
            onAdd.accept(new ComponentCoordinate.Simple(componentList.getSelectedValue(), versionList.getSelectedValue()));
            this.dispose();
        });

        versionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && versionList.getSelectedValue() != null && componentList.getSelectedValue() != null) {
                    okBtn.doClick();
                }
            }
        });

        cancelBtn.addActionListener(e -> this.dispose());

        buttons.add(okBtn);
        buttons.add(cancelBtn);

        this.getContentPane().add(buttons, BorderLayout.SOUTH);
        this.pack();
        this.setLocationRelativeTo(parent);
        this.setVisible(true);
    }

    private static class ComponentCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof String pkg) {
                var meta = Main.METADATA.getComponentMeta(pkg).getNow(null);
                if (meta != null) {
                    this.setText(meta.name());
                }
            }

            return this;
        }
    }
}
