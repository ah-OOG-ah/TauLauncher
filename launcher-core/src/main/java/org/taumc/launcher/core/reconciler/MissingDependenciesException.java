package org.taumc.launcher.core.reconciler;

import lombok.Getter;
import org.taumc.launcher.core.meta.json.Requirement;

import java.util.List;

public class MissingDependenciesException extends Exception {
    @Getter
    private final List<Requirement> additionalDependencies;

    public MissingDependenciesException(List<Requirement> additionalDependencies) {
        super("Additional dependencies needed");
        this.additionalDependencies = additionalDependencies;
    }
}
