package org.taumc.launcher.gui.screens.mods;

import org.taumc.launcher.core.mods.DownloadableFile;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public class DownloadableFileCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof DownloadableFile file) {
            setText(file.fileName() + " [" + file.releaseType().name().toLowerCase(Locale.ROOT) + "]");
        }

        return this;
    }
}
