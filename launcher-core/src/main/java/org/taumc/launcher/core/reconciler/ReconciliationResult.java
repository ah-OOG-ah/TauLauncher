package org.taumc.launcher.core.reconciler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.taumc.launcher.core.launch.RuntimeInstance;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public record ReconciliationResult(Map<InstanceFile, PathPopulator> managedPaths,
                                   Consumer<RuntimeInstance> instanceConfigurer,
                                   @Nullable AutoCloseable closeFunction) {
    public static final ReconciliationResult EMPTY = new ReconciliationResult(Map.of(), i -> {}, null);

    public ReconciliationResult mergeWith(Collection<ReconciliationResult> otherResults) {
        Map<InstanceFile, PathPopulator> managedPaths = new HashMap<>(this.managedPaths);
        for (var result : otherResults) {
            managedPaths.putAll(result.managedPaths);
        }
        var configurers = otherResults.stream().map(r -> r.instanceConfigurer).toList();
        var closers = otherResults.stream().map(r -> r.closeFunction).filter(Objects::nonNull).toList();
        AutoCloseable mergedCloser = closers.isEmpty() ? null : () -> {
            for (var closer : closers) {
                closer.close();
            }
        };
        return new ReconciliationResult(Map.copyOf(managedPaths),
                i -> configurers.forEach(c -> c.accept(i)),
                mergedCloser);
    }

    @Override
    public @NotNull String toString() {
        return "ReconciliationResult with " + managedPaths.size() + " managed paths";
    }
}
