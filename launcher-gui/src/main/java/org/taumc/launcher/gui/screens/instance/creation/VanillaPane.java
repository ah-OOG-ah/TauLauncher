package org.taumc.launcher.gui.screens.instance.creation;

import static java.util.Comparator.comparing;
import static javax.swing.BoxLayout.X_AXIS;
import static javax.swing.BoxLayout.Y_AXIS;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.SwingConstants.CENTER;
import static org.taumc.launcher.core.meta.prism.HTTPMetaRepository.prism;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.STD_DIM_MAX_TEXTFIELD;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.STD_MARGIN;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.THIN_BORDER;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.boxPanel;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.wrapWithMargin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import org.taumc.launcher.core.meta.json.PackageIndex;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;
import org.taumc.launcher.gui.components.VersionTable;

public class VanillaPane extends JPanel {
    private final LinkedHashMap<String, String> RELEASE_NAMES = new LinkedHashMap<>();
    private final HashMap<String, ArrayList<PackageIndex.Version>> RELEASES = new HashMap<>();
    // TODO: do this better
    private final LinkedHashMap<String, String> LOADER_UIDS = new LinkedHashMap<>();
    private final LinkedHashMap<String, ArrayList<PackageIndex.Version>> LOADER_REVS = new LinkedHashMap<>();
    private final HTTPMetaRepository PRISM_META = prism();

    public VanillaPane() {
        super();
        setLayout(new BoxLayout(this, Y_AXIS));

        loadMCVersions();
        loadLoaderVersions();

        add(createTitlePane());
        add(createMainboxPane());
    }

    /**
     * @param typeUnchecked Any object.
     * @return If the input is a valid release type, it returns unchanged. If any other object, it returns "unknown". If
     *         the input is null, returns ""
     */
    private String validateReleaseType(Object typeUnchecked) {
        if (typeUnchecked == null) return "";
        return RELEASE_NAMES.values().stream()
                .filter(t -> t.equals(typeUnchecked))
                .findAny()
                .orElse("unknown");
    }

    private void loadMCVersions() {
        RELEASE_NAMES.put("Releases", "release");
        RELEASE_NAMES.put("Snapshots", "snapshot");
        RELEASE_NAMES.put("Old Snapshots", "old_snapshot");
        RELEASE_NAMES.put("Betas", "old_beta");
        RELEASE_NAMES.put("Alphas", "old_alpha");
        RELEASE_NAMES.put("Experiments", "experiment");

        try {
            // Loading is synchronous, to make sure that once this method exits the lists are untouched
            // After that, only the Swing event thread gets to touch them.
            PRISM_META.getPackageIndex("net.minecraft").get().versions().forEach(v -> {
                var type = validateReleaseType(v.properties().get("type"));
                var list = RELEASES.computeIfAbsent(type, k -> new ArrayList<>());
                list.add(v);
            });
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadLoaderVersions() {
        LOADER_UIDS.put("Forge", "net.minecraftforge");
        LOADER_UIDS.put("NeoForge", "net.neoforged");
        LOADER_UIDS.put("Fabric", "net.fabricmc.fabric-loader");
        LOADER_UIDS.put("Quilt", "org.quiltmc.quilt-loader");
        LOADER_UIDS.put("LiteLoader", "com.mumfrey.liteloader");

        LOADER_UIDS.forEach((name, uid) -> {
            try {
                // See loadMCVersions - this is forced to be synchronous for a reason
                PRISM_META.getPackageIndex(uid).get().versions().forEach(v ->
                        LOADER_REVS.computeIfAbsent(uid, k -> new ArrayList<>()).add(v)
                );
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static JPanel createTitlePane() {
        var titlePane = boxPanel(X_AXIS);
        titlePane.setBorder(STD_MARGIN);

        var title = new JLabel("<html><h2>Vanilla</h2></html>");
        title.setMaximumSize(STD_DIM_MAX_TEXTFIELD);
        titlePane.add(title);

        titlePane.add(new JLabel(InstanceCreationModel.Type.VANILLA.icon));

        return titlePane;
    }

    private JPanel createMainboxPane() {
        var mainboxPane = boxPanel(Y_AXIS);
        mainboxPane.setBorder(THIN_BORDER);

        mainboxPane.add(createVersionPane());
        mainboxPane.add(createModloaderPane());

        return mainboxPane;
    }

    private JPanel createVersionPane() {
        var versionPane = boxPanel(X_AXIS);
        versionPane.setBorder(STD_MARGIN);

        var versionTable = new VersionTable();

        var versionTableViewport = new JScrollPane(versionTable, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        versionPane.add(wrapWithMargin(versionTableViewport, THIN_BORDER));

        var filterPane = boxPanel(Y_AXIS);
        filterPane.setBorder(STD_MARGIN);

        var filterLabel = new JLabel("Filter", null, CENTER);
        filterLabel.setMaximumSize(STD_DIM_MAX_TEXTFIELD);
        filterPane.add(filterLabel);

        JCheckBox releaseCheck = null;
        for (var type : RELEASE_NAMES.keySet()) {
            var check = new JCheckBox(type);
            filterPane.add(check);
            if (releaseCheck == null) releaseCheck = check;

            check.setActionCommand(RELEASE_NAMES.get(type));
            check.addActionListener(al -> {
                var listName = al.getActionCommand();
                var list = RELEASES.get(listName);

                if (versionTable.hasList(listName))
                    versionTable.depopulate(listName, list);
                else
                    versionTable.populate(listName, list);
            });
        }
        assert releaseCheck != null;
        releaseCheck.doClick();

        versionPane.add(filterPane);

        return versionPane;
    }

    private JPanel createModloaderPane() {
        var modloaderPane = boxPanel(X_AXIS);
        modloaderPane.setBorder(STD_MARGIN);

        var loaderTable = new VersionTable();

        var loaderTableViewport = new JScrollPane(loaderTable, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        modloaderPane.add(wrapWithMargin(loaderTableViewport, THIN_BORDER));

        var loaderRadioPane = boxPanel(Y_AXIS);
        loaderRadioPane.setBorder(STD_MARGIN);

        var loaderLabel = new JLabel("Mod Loader", null, CENTER);
        loaderLabel.setMaximumSize(STD_DIM_MAX_TEXTFIELD);
        loaderRadioPane.add(loaderLabel);

        var loaderButtonGroup = new ButtonGroup();

        var noneButton = new JRadioButton("None");
        loaderRadioPane.add(noneButton);
        loaderButtonGroup.add(noneButton);
        for (var loader : LOADER_UIDS.entrySet()) {
            var radio = new JRadioButton(loader.getKey());
            loaderRadioPane.add(radio);
            loaderButtonGroup.add(radio);

            radio.setActionCommand(loader.getValue());
            radio.addActionListener(al -> {
                var listName = al.getActionCommand();

                if (loaderTable.hasList(listName)) return;

                loaderTable.clear();
                loaderTable.populate(listName, LOADER_REVS.get(listName));
            });
        }
        noneButton.doClick();
        modloaderPane.add(loaderRadioPane);

        return modloaderPane;
    }
}
