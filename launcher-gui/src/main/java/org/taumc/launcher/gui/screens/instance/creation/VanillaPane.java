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

public class VanillaPane extends JPanel {
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

        versionSelectTableModel.addRow(new Version("15w49a", "12/12/12", "alpha"));
        versionSelectTableModel.addRow(new Version("15w49b", "12/12/13", "alpha"));
        versionSelectTableModel.addRow(new Version("15w49c", "12/12/14", "release"));

        var versionSelectTable = new FlatTable();
        versionSelectTable.setModel(versionSelectTableModel);
        versionSelectTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        versionPane.add(wrapWithMargin(versionSelectTable, Utils.THIN_BORDER));

        var filterPane = Utils.boxPanel(BoxLayout.Y_AXIS);
        filterPane.add(new JLabel("Filter"));
        for (var type : Version.Type.values()) {
            filterPane.add(new JCheckBox(type.name));
        }

        versionPane.add(filterPane);

        return versionPane;
    }

    private static class VersionTableModel extends AbstractTableModel {
        private static final String[] colNames = {
                "Version", "Release Date", "Release Type"
        };

        private final ArrayList<Version> data = new ArrayList<>();

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
            return data.get(row).get(col);
        }

        public void addRow(Version version) {
            data.add(version);
        }

        public String[] getColumnNames() {
            return colNames;
        }
    }

    private record Version(String name, String release, String type) {
        public String get(int idx) {
            return switch (idx) {
                case 0 -> name;
                case 1 -> release;
                case 2 -> type;
                default -> throw new IllegalArgumentException(String.valueOf(idx));
            };
        }

        private enum Type {
            RELEASE("release", "Releases"),
            SNAPSHOT("snapshot", "Snapshots"),
            OLD_SNAPSHOT("old_snapshot", "Old Snapshots"),
            BETA("old_beta", "Betas"),
            ALPHA("old_alpha", "Alphas"),
            EXPERIMENT("experiment", "Experiments");

            public final String id;
            public final String name;

            Type(String id, String name) {
                this.id = id;
                this.name = name;
            }
        }
    }
}
