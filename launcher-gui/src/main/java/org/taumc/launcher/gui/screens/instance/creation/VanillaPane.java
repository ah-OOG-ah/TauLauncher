package org.taumc.launcher.gui.screens.instance.creation;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class VanillaPane extends JPanel {
    public VanillaPane() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        var titlePane = Utils.boxPanel(BoxLayout.X_AXIS);
        titlePane.setBorder(Utils.STD_MARGIN);

        var title = new JLabel("Vanilla");
        title.setMaximumSize(Utils.STD_DIM_MAX_TEXTFIELD);
        titlePane.add(title);

        titlePane.add(new JLabel(InstanceCreationModel.Type.VANILLA.icon));

        add(titlePane);
    }
}
