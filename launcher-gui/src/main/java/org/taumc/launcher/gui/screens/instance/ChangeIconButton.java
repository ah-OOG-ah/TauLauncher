package org.taumc.launcher.gui.screens.instance;

import java.awt.Dimension;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.taumc.launcher.gui.icon.IconRegistry;

public class ChangeIconButton extends JButton {
    private static final int margin = 16;

    public ChangeIconButton(ImageIcon icon) {
        super(icon);

        setMinimumSize(new Dimension(IconRegistry.ICON_HEIGHT + margin, IconRegistry.ICON_HEIGHT + margin));
        setPreferredSize(getMinimumSize());
        setMaximumSize(getMinimumSize());
    }
}
