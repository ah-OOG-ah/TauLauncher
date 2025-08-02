package org.taumc.launcher.core.reconciler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.reconciler.intervention.UserInterventionHandler;
import org.taumc.launcher.core.reconciler.tree.ComponentTreeNode;
import org.taumc.launcher.core.util.SetUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

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

    public static void migrateInstance(Path instancePath, ComponentTreeNode oldRoot, ComponentTreeNode newRoot, MetadataService metadataService, ProgressProvider progressProvider, UserInterventionHandler interventionHandler) throws Exception {
        var oldReconciler = new Reconciler(oldRoot, metadataService, progressProvider, interventionHandler);
        var newReconciler = new Reconciler(newRoot, metadataService, progressProvider, interventionHandler);
        try (var oldOutput = oldReconciler.runReconciliation(ReconciliationOptions.builder().updateMode(ReconciliationOptions.UpdateMode.UPDATE_IF_MISSING).build());
             var newOutput = newReconciler.runReconciliation(ReconciliationOptions.builder().updateMode(ReconciliationOptions.UpdateMode.UPDATE_IF_DIFFERENT).build())) {
            var difference = SetUtils.diffSets(oldOutput.result().managedPaths().keySet(), newOutput.result().managedPaths().keySet());

            // Apply the new reconciler's output
            newOutput.applyToFilesystem(progressProvider, instancePath).join();

            ArrayList<InstanceFile> pathsToRemove = new ArrayList<>(difference.removed());

            while (!pathsToRemove.isEmpty()) {
                pathsToRemove.sort(Comparator.comparingInt(InstanceFile::getNameCount).reversed());

                Set<InstanceFile> touchedDirs = new LinkedHashSet<>();

                // Delete files that are no longer needed
                for (var remove : pathsToRemove) {
                    LOGGER.info("Deleting {} as it's no longer referenced by a managed component", remove);
                    Path nioPath = remove.toPath(instancePath);
                    if (remove.parent() != null) {
                        touchedDirs.add(remove.parent());
                    }
                    Files.deleteIfExists(nioPath);
                }

                pathsToRemove.clear();

                for (var dir : touchedDirs) {
                    Path nioPath = dir.toPath(instancePath);
                    if (Files.isDirectory(nioPath)) {
                        boolean isEmpty;
                        try (DirectoryStream<Path> stream = Files.newDirectoryStream(nioPath)) {
                            isEmpty = !stream.iterator().hasNext();
                        }
                        if (isEmpty) {
                            pathsToRemove.add(dir);
                        }
                    }
                }
            }
        }
    }
}
