package org.taumc.launcher.core.reconciler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.reconciler.intervention.UserInterventionHandler;
import org.taumc.launcher.core.reconciler.tree.ComponentTreeNode;
import org.taumc.launcher.core.util.SetUtils;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public class ReconciliationHelpers {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReconciliationHelpers.class);

    public static void migrateInstance(Path instancePath, ComponentTreeNode oldRoot, ComponentTreeNode newRoot, MetadataService metadataService, ProgressProvider progressProvider, UserInterventionHandler interventionHandler) throws Exception {
        var oldReconciler = new Reconciler(oldRoot, metadataService, progressProvider, interventionHandler);
        var newReconciler = new Reconciler(newRoot, metadataService, progressProvider, interventionHandler);
        try (var oldOutput = oldReconciler.runReconciliation(ReconciliationOptions.builder().build());
             var newOutput = newReconciler.runReconciliation(ReconciliationOptions.builder().build())) {
            var difference = SetUtils.diffSets(oldOutput.result().managedPaths().keySet(), newOutput.result().managedPaths().keySet());

            // Apply the new reconciler's output
            newOutput.applyToFilesystem(progressProvider, instancePath, ReconciliationOptions.UpdateMode.UPDATE_IF_DIFFERENT).join();

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
