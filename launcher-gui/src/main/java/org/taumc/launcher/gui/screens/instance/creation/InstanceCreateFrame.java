package org.taumc.launcher.gui.screens.instance.creation;

import static org.taumc.launcher.gui.screens.instance.creation.Utils.HALF_MARGIN;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.STD_BORDER;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.STD_DIM_MAX_TEXTFIELD;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.STD_DIM_TEXTFIELD;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.STD_MARGIN;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.boxPanel;
import static org.taumc.launcher.gui.screens.instance.creation.Utils.wrapWithMargin;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.components.FlatTextField;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

public class InstanceCreateFrame extends JFrame {

    private final LinkedHashMap<InstanceCreationModel.Type, JPanel> typePanelMap = new LinkedHashMap<>();
    private JPanel configPanelHolder;

    public InstanceCreateFrame() {
        var content = boxPanel(BoxLayout.Y_AXIS);
        setContentPane(content);

        content.add(createIconNameGroupPane());
        content.add(createTypeConfigPane());

        setMinimumSize(content.getMinimumSize());
    }

    private static JPanel createIconNameGroupPane() {
        var iconNameGroupPane = boxPanel(BoxLayout.X_AXIS);
        iconNameGroupPane.setBorder(STD_BORDER);

        var icon = new ChangeIconButton(new FlatSVGIcon("taulauncher/icons/poly/new.svg"));
        iconNameGroupPane.add(wrapWithMargin(icon, STD_MARGIN));

        iconNameGroupPane.add(createNameGroupPane());

        return iconNameGroupPane;
    }

    private static JPanel createNameGroupPane() {
        var nameGroupPane = boxPanel(BoxLayout.Y_AXIS);
        nameGroupPane.setBorder(STD_MARGIN);

        var nameSelect = new FlatTextField();
        nameSelect.setLeadingComponent(new JLabel("Name: ", null, SwingConstants.LEFT));
        nameSelect.setMinimumSize(STD_DIM_TEXTFIELD);
        nameSelect.setPreferredSize(STD_DIM_TEXTFIELD);
        nameSelect.setMaximumSize(STD_DIM_MAX_TEXTFIELD);
        nameGroupPane.add(wrapWithMargin(nameSelect, HALF_MARGIN));

        var groupSelect = new FlatTextField();
        groupSelect.setLeadingComponent(new JLabel("Group: ", null, SwingConstants.LEFT));
        groupSelect.setMinimumSize(STD_DIM_TEXTFIELD);
        groupSelect.setPreferredSize(STD_DIM_TEXTFIELD);
        groupSelect.setMaximumSize(STD_DIM_MAX_TEXTFIELD);
        nameGroupPane.add(wrapWithMargin(groupSelect, HALF_MARGIN));

        return nameGroupPane;
    }

    private JPanel createTypeConfigPane() {
        var typeConfigPane = boxPanel(BoxLayout.X_AXIS);
        typeConfigPane.setBorder(STD_BORDER);

        typeConfigPane.add(createTypePane());

        configPanelHolder = new JPanel(new CardLayout());
        createConfigPanes();
        typeConfigPane.add(configPanelHolder);

        return typeConfigPane;
    }

    private JPanel createTypePane() {
        var typePane = boxPanel(BoxLayout.Y_AXIS);
        typePane.setBorder(STD_BORDER);

        var typeListModel = new DefaultListModel<InstanceCreationModel.Type>();
        var typeList = new JList<>(typeListModel);
        typeList.setCellRenderer(new LabelListRenderer());
        typeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        typeList.setSelectedIndex(0);

        for (var type : InstanceCreationModel.TYPES) {
            typeListModel.addElement(type);
        }

        typeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                var selected = typeList.getSelectedValue();
                setCurrentPage(selected);
            }
        });

        typePane.add(typeList);

        var spacer = new JPanel();
        spacer.setMaximumSize(new Dimension(-1, Integer.MAX_VALUE));
        typePane.add(spacer);

        return typePane;
    }

    private void createConfigPanes() {
        for (var type : InstanceCreationModel.TYPES) {
            var panel = setupConfigPane(type.panelSupplier.get());
            typePanelMap.put(type, panel);
            configPanelHolder.add(type.name, panel);
        }
    }

    private JPanel setupConfigPane(JPanel configPane) {
        configPane.setBorder(STD_MARGIN);

        // Arbitrary size similar to what Poly does
        var minSize = new Dimension(500, 600);
        configPane.setMinimumSize(minSize);
        configPane.setPreferredSize(minSize);
        configPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        return configPane;
    }

    private void setCurrentPage(InstanceCreationModel.Type page) {
        if (!typePanelMap.containsKey(page)) {
            throw new IllegalArgumentException(page.name);
        }
        CardLayout cl = (CardLayout) configPanelHolder.getLayout();
        cl.show(configPanelHolder, page.name);
    }

}
