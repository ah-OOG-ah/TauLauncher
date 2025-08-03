package org.taumc.launcher.core.reconciler;

import java.io.IOException;
import java.nio.file.Path;

public interface PopulatableEntry {
    void populateOnDisk(Path locationOnDisk) throws IOException;
    boolean needsUpdate(Path destinationPath, ReconciliationOptions.UpdateMode updateMode);
}
