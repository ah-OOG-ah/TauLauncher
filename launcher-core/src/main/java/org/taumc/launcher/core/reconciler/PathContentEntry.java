package org.taumc.launcher.core.reconciler;

import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

@Accessors(fluent = true)
public record PathContentEntry(Path locationInComponent) implements PopulatableEntry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathContentEntry.class);

    public PathContentEntry {
        Objects.requireNonNull(locationInComponent);
    }

    @Override
    public boolean needsUpdate(Path destinationPath, ReconciliationOptions.UpdateMode updateMode) {
        boolean needCopy = false;

        try {
            if (updateMode == ReconciliationOptions.UpdateMode.UPDATE_IF_MISSING) {
                needCopy = !Files.exists(destinationPath);
            } else if (updateMode == ReconciliationOptions.UpdateMode.UPDATE_IF_DIFFERENT) {
                needCopy = Files.size(destinationPath) != Files.size(locationInComponent()) || Files.mismatch(locationInComponent(), destinationPath) != -1;
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

    @Override
    public void populateOnDisk(Path locationOnDisk) throws IOException {
        Files.createDirectories(locationOnDisk.getParent());

        try (var in = Files.newInputStream(locationInComponent())) {
            Files.copy(in, locationOnDisk, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
