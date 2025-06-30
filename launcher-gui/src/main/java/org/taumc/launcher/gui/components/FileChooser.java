package org.taumc.launcher.gui.components;

import java.io.File;
import java.util.List;

public abstract class FileChooser {
    protected boolean allowMultiSelection = false;

    protected FileChooser() {

    }

    public static FileChooser getInstance() {
        return new SwingFileChooser();
    }

    public abstract List<File> showFilePicker();

    public final void setAllowMultiSelection(boolean flag) {
        this.allowMultiSelection = flag;
    }
}
