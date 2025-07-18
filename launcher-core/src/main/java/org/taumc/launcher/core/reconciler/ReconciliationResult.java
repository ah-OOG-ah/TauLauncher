package org.taumc.launcher.core.reconciler;

import org.taumc.launcher.core.launch.RuntimeInstance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public record ReconciliationResult(Set<Path> managedPaths, Consumer<RuntimeInstance> instanceConfigurer) {
    public static final ReconciliationResult EMPTY = new ReconciliationResult(Set.of(), i -> {});

    public ReconciliationResult mergeWith(Collection<ReconciliationResult> otherResults) {
        Set<Path> managedPaths = new HashSet<>(this.managedPaths);
        for (var result : otherResults) {
            managedPaths.addAll(result.managedPaths);
        }
        var configurers = otherResults.stream().map(r -> r.instanceConfigurer).toList();
        return new ReconciliationResult(Set.copyOf(managedPaths), i -> configurers.forEach(c -> c.accept(i)));
    }
}
