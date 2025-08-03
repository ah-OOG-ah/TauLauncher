package org.taumc.launcher.core.reconciler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.taumc.launcher.core.launch.RuntimeInstance;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public record ReconciliationResult(Map<InstanceFile, PopulatableEntry> managedPaths,
                                   Consumer<RuntimeInstance> instanceConfigurer,
                                   @Nullable AutoCloseable closeFunction) {
    public static final ReconciliationResult EMPTY = new ReconciliationResult(Map.of(), i -> {}, null);

    public static ReconciliationResult configuring(Consumer<RuntimeInstance> consumer) {
        return new ReconciliationResult(Map.of(), consumer, null);
    }

    public ReconciliationResult mergeWith(Collection<ReconciliationResult> otherResults) {
        Map<InstanceFile, PopulatableEntry> managedPaths = new HashMap<>();
        for (var result : otherResults) {
            managedPaths.putAll(result.managedPaths);
        }
        managedPaths.putAll(this.managedPaths);
        var configurers = Stream.of(otherResults.stream().map(r -> r.instanceConfigurer), Stream.of(this.instanceConfigurer)).flatMap(Function.identity()).toList();
        var closers = Stream.of(otherResults.stream().map(r -> r.closeFunction), Stream.of(this.closeFunction)).flatMap(Function.identity()).filter(Objects::nonNull).toList();
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
