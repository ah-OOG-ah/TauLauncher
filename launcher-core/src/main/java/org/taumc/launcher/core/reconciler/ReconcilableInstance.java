package org.taumc.launcher.core.reconciler;

import org.taumc.launcher.core.meta.json.Requirement;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.util.List;

public interface ReconcilableInstance {
    ProgressProvider getProgressProvider();
    void validateRequirements(List<Requirement> requirements) throws MissingDependenciesException;
}
