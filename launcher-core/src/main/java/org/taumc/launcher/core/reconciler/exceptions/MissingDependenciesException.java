package org.taumc.launcher.core.reconciler.exceptions;

import lombok.Getter;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;

import java.util.List;

public final class MissingDependenciesException extends RecoverableReconcilerException {
    @Getter
    private final List<ReconcilableGameComponent> additionalComponents;

    public MissingDependenciesException(List<ReconcilableGameComponent> additionalComponents) {
        super("Additional dependencies needed");
        this.additionalComponents = additionalComponents;
    }
}
