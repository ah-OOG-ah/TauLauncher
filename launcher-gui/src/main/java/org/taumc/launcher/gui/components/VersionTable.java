package org.taumc.launcher.gui.components;

import static java.util.Comparator.comparing;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import org.taumc.launcher.core.meta.json.PackageIndex;
import org.taumc.launcher.gui.icon.IconRegistry;
import org.taumc.launcher.gui.screens.instance.creation.VanillaPane;

public class VersionTable extends JTable {
    private final VersionTableModel model;

    public VersionTable() {
        super(new VersionTableModel(), null, null);

        model = (VersionTableModel) getModel();
        getColumn("Version").setCellRenderer(new VersionCellRenderer());
        setSelectionMode(SINGLE_SELECTION);
    }

    public boolean hasList(String listName) {
        return model.hasList(listName);
    }

    public void populate(String name, List<PackageIndex.Version> versions) {
        model.populate(name, versions);
        repaint();
    }

    public void depopulate(String name, List<PackageIndex.Version> versions) {
        model.depopulate(name, versions);
        repaint();
    }

    public void clear() {
        model.clear();
        repaint();
    }

    private static class VersionTableModel extends AbstractTableModel {
        private static final String[] colNames = {
                "Version", "Release Date", "Release Type"
        };

        private final ArrayList<PackageIndex.Version> data = new ArrayList<>();
        private final HashSet<String> datasetNames = new HashSet<>();

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
            return (ver.recommended() && col == 0 ? "REC" : "") + switch (col) {
                case 0 -> ver.version();
                case 1 -> ver.releaseTime();
                case 2 -> {
                    var props = ver.properties();
                    if (props == null) yield "unknown";
                    else yield ver.properties().getOrDefault("type", "unknown");
                }
                default -> throw new IllegalArgumentException("Invalid column " + col);
            };
        }

        private boolean hasList(String name) {
            return datasetNames.contains(name);
        }

        private void populate(String name, List<PackageIndex.Version> versions) {
            if (!datasetNames.add(name)) return;

            data.addAll(versions);
            data.sort(comparing(PackageIndex.Version::releaseTime).reversed());
        }

        private void depopulate(String name, List<PackageIndex.Version> versions) {
            if (!datasetNames.remove(name)) return;

            data.removeAll(versions);
        }

        private void clear() {
            datasetNames.clear();
            data.clear();
        }
    }

    // TODO: decide whether the performance oj raw JLabel table cell renderers is an issue
    private static class VersionCellRenderer implements TableCellRenderer {
        private static final ImageIcon STAR = IconRegistry.loadBuiltinIcon("poly/star", 4);
        private static final ImageIcon EMPTY = IconRegistry.loadBuiltinIcon("empty", 4);
        private final JLabel RECOMMENDED = new JLabel(null, STAR, SwingConstants.LEFT);
        private final JLabel NORMAL = new JLabel(null, EMPTY, SwingConstants.LEFT);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            var val = (String) value;

            if (val.startsWith("REC")) {
                val = val.substring(3);
                RECOMMENDED.setText(val);
                return RECOMMENDED;
            } else {
                NORMAL.setText(val);
                return NORMAL;
            }
        }
    }
}
