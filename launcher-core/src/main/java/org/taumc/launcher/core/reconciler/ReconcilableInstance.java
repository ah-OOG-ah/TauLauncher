package org.taumc.launcher.core.reconciler;

import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.meta.json.Requirement;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.reconciler.exceptions.MissingDependenciesException;

import java.util.List;

public interface ReconcilableInstance {
    ProgressProvider getProgressProvider();
    MetadataService getMetadataService();
    void validateRequirements(List<Requirement> requirements) throws MissingDependenciesException;
    void validateComponents(List<ReconcilableGameComponent> components) throws MissingDependenciesException;
}
