package org.taumc.launcher.gui.screens.instance;

import org.taumc.launcher.gui.Main;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.meta.json.Requirement;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class NewComponentDialog extends JDialog {
    private final List<MMCPack.Component> existingComponents;

    private static final Set<String> IGNORED_DEPENDENCIES = Set.of("org.lwjgl", "org.lwjgl3");

    public NewComponentDialog(Frame parent, List<MMCPack.Component> existingComponents, Consumer<ComponentCoordinate> onAdd) {
        this(parent, existingComponents, onAdd, null);
    }

    public NewComponentDialog(Frame parent, List<MMCPack.Component> existingComponents, Consumer<ComponentCoordinate> onAdd, ComponentCoordinate initialSelection) {
        super(parent, "Add Component", true);
        this.existingComponents = existingComponents;

        Map<String, List<String>> options = new HashMap<>();

        Predicate<Requirement> requirementsSatisfied = r -> r.isSatisfied(existingComponents, Main.METADATA);
        // Compute the valid components to add
        for (String pkg : Main.METADATA.getKnownPackages()) {
            LinkedHashSet<String> versions = new LinkedHashSet<>();
            boolean ignoringRequirements = pkg.equals("net.minecraft");
            for (var idx : Main.METADATA.getPackageIndexes(pkg)) {
                for (var version : idx.index().versions()) {
                    if (ignoringRequirements || version.requires() == null || version.requires().stream().allMatch(requirementsSatisfied)) {
                        versions.add(version.version());
                    }
                }
            }
            if (!versions.isEmpty()) {
                options.put(pkg, List.copyOf(versions));
            }
        }

        DefaultListModel<String> componentModel = new DefaultListModel<>();
        options.keySet().forEach(componentModel::addElement);
        JList<String> componentList = new JList<>(componentModel);
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

        // Layout
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                componentScroll,
                versionScroll
        );
        splitPane.setDividerLocation(150);
        splitPane.setResizeWeight(0.3);

        // Create a modal dialog with the custom panel
        this.getContentPane().add(splitPane, BorderLayout.CENTER);

        // Add OK and Cancel buttons
        JPanel buttons = new JPanel();
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");

        okBtn.addActionListener(e -> {
            onAdd.accept(new MMCPack.Component(componentList.getSelectedValue(), versionList.getSelectedValue()));
            this.dispose();
        });

        cancelBtn.addActionListener(e -> this.dispose());

        buttons.add(okBtn);
        buttons.add(cancelBtn);

        if (initialSelection != null && options.getOrDefault(initialSelection.uid(), List.of()).contains(initialSelection.version())) {
            componentList.setSelectedValue(initialSelection.uid(), true);
            versionList.setSelectedValue(initialSelection.version(), true);
        }

        this.getContentPane().add(buttons, BorderLayout.SOUTH);
        this.pack();
        this.setLocationRelativeTo(parent);
        this.setVisible(true);
    }
}
