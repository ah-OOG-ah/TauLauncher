package org.taumc.launcher.core.reconciler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class ReconciliationHelpers {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReconciliationHelpers.class);

    public static boolean fileNeedsUpdate(Path sourcePath, Path destinationPath, ReconciliationOptions.UpdateMode updateMode) {
        boolean needCopy = false;

        try {
            if (updateMode == ReconciliationOptions.UpdateMode.UPDATE_IF_MISSING) {
                needCopy = !Files.exists(destinationPath);
            } else if (updateMode == ReconciliationOptions.UpdateMode.UPDATE_IF_DIFFERENT) {
                needCopy = Files.size(destinationPath) != Files.size(sourcePath) || Files.mismatch(sourcePath, destinationPath) != -1;
                if (needCopy) {
                    LOGGER.info("Updating {} due to content mismatch", destinationPath.getFileName().toString());
                }
            }
        } catch (NoSuchFileException e) {
            LOGGER.info("Updating {} due to not existing", destinationPath.getFileName().toString());
            needCopy = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return needCopy;
    }
}
