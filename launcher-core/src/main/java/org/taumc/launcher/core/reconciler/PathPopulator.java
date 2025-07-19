package org.taumc.launcher.core.reconciler;

import java.io.IOException;
import java.nio.file.Path;

public interface PathPopulator {
    void populate(Path destinationFile) throws IOException;
}
