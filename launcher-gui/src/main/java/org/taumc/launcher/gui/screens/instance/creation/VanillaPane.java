package org.taumc.launcher.gui.screens.instance.creation;

import static org.taumc.launcher.gui.screens.instance.creation.Utils.wrapWithMargin;

import com.formdev.flatlaf.extras.components.FlatTable;
import java.awt.Color;
import java.util.ArrayList;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import org.taumc.launcher.core.meta.json.PackageIndex;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;

public class VanillaPane extends JPanel {
    private static final String[] VANILLA_RELEASE_TYPES = {
            "Releases",
            "Snapshots",
            "Old Snapshots",
            "Betas",
            "Alphas",
            "Experiments"
    };

    public VanillaPane() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(createTitlePane());
        add(createMainboxPane());
    }

    public static JPanel createTitlePane() {
        var titlePane = Utils.boxPanel(BoxLayout.X_AXIS);
        titlePane.setBorder(Utils.STD_MARGIN);

        var title = new JLabel("Vanilla");
        title.setMaximumSize(Utils.STD_DIM_MAX_TEXTFIELD);
        titlePane.add(title);

        titlePane.add(new JLabel(InstanceCreationModel.Type.VANILLA.icon));

        return titlePane;
    }

    public static JPanel createMainboxPane() {
        var mainboxPane = Utils.boxPanel(BoxLayout.Y_AXIS);
        mainboxPane.setOpaque(true);
        mainboxPane.setBackground(Color.BLUE);
        mainboxPane.setBorder(Utils.THIN_BORDER);

        mainboxPane.add(createVersionPane());

        return mainboxPane;
    }

    public static JPanel createVersionPane() {
        var versionPane = Utils.boxPanel(BoxLayout.X_AXIS);
        versionPane.setBorder(Utils.STD_MARGIN);

        var versionSelectTableModel = new VersionTableModel();
        HTTPMetaRepository.prism().getMinecraftIndex().versions().forEach(versionSelectTableModel::addRow);

        var versionSelectTable = new FlatTable();
        versionSelectTable.setModel(versionSelectTableModel);
        versionSelectTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        versionPane.add(wrapWithMargin(versionSelectTable, Utils.THIN_BORDER));

        var filterPane = Utils.boxPanel(BoxLayout.Y_AXIS);
        filterPane.add(new JLabel("Filter"));
        for (var type : VANILLA_RELEASE_TYPES) {
            filterPane.add(new JCheckBox(type));
        }

        versionPane.add(filterPane);

        return versionPane;
    }

    private static class VersionTableModel extends AbstractTableModel {
        private static final String[] colNames = {
                "Version", "Release Date", "Release Type"
        };

        private final ArrayList<PackageIndex.Version> data = new ArrayList<>();

        @Override
        public int getColumnCount() {
            return colNames.length;
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public String getColumnName(int idx) {
            return colNames[idx];
        }

        @Override
        public Object getValueAt(int row, int col) {
            var ver = data.get(row);
            return switch (col) {
                case 0 -> ver.version();
                case 1 -> ver.releaseTime();
                case 2 -> ver.properties().getOrDefault("type", "unknown");
                default -> throw new IllegalArgumentException("Invalid column " + col);
            };
        }

        public void addRow(PackageIndex.Version version) {
            data.add(version);
        }

        public String[] getColumnNames() {
            return colNames;
        }
    }
}
