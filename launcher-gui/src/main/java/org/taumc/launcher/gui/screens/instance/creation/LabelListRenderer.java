package org.taumc.launcher.gui.screens.instance.creation;

import java.awt.Component;
import javax.swing.JList;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;

public class LabelListRenderer extends JToggleButton implements ListCellRenderer<InstanceCreationModel.Type> {
    @Override
    public Component getListCellRendererComponent(
            JList<? extends InstanceCreationModel.Type> list,
            InstanceCreationModel.Type value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
        setSelected(isSelected);
        setText(value.name);
        setIcon(value.icon);
        return this;
    }
}
