package org.taumc.launcher.core.reconciler;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public record ReconciliationResult(Set<Path> managedPaths) {
    public static final ReconciliationResult EMPTY = new ReconciliationResult(Set.of());

    public ReconciliationResult mergeWith(Collection<ReconciliationResult> otherResults) {
        Set<Path> managedPaths = new HashSet<>(this.managedPaths);
        for (var result : otherResults) {
            managedPaths.addAll(result.managedPaths);
        }
        return new ReconciliationResult(Set.copyOf(managedPaths));
    }
}
