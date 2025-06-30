package org.taumc.launcher.gui.components;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class SwingFileChooser extends FileChooser {
    public final JFileChooser fileChooser = new JFileChooser();

    SwingFileChooser() {

    }

    @Override
    public List<File> showFilePicker() {
        fileChooser.setMultiSelectionEnabled(allowMultiSelection);
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return List.of(fileChooser.getSelectedFiles());
        } else {
            return List.of();
        }
    }
}
