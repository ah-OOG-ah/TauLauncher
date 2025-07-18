package org.taumc.launcher.core.reconciler;

import org.jetbrains.annotations.Nullable;
import org.taumc.launcher.core.meta.json.Requirement;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public interface ReconcilableInstance {
    @Nullable
    Path getInstancePath();

    default Path getBaseResolutionPath() {
        var instancePath = getInstancePath();
        if (instancePath != null) {
            return instancePath;
        } else {
            return Paths.get("");
        }
    }

    ProgressProvider getProgressProvider();
    void validateRequirements(List<Requirement> requirements) throws MissingDependenciesException;
}
