package org.taumc.launcher.core.reconciler;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.launch.RuntimeInstance;
import org.taumc.launcher.core.meta.component.GameComponent;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.meta.json.Requirement;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Reconciler implements ReconcilableInstance {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeInstance.class);

    private final Map<String, ReconcilableGameComponent> initialComponents;
    @Getter
    private final Map<String, ReconcilableGameComponent> components;
    @Getter
    private final Path instancePath;
    private final MetadataService metadataService;
    @Getter
    private final ProgressProvider progressProvider;

    public Reconciler(Path instancePath, Map<String, ReconcilableGameComponent> initialComponents, MetadataService metadataService, ProgressProvider progressProvider) {
        this.instancePath = instancePath;
        this.initialComponents = Map.copyOf(initialComponents);
        this.metadataService = metadataService;
        this.progressProvider = progressProvider;
        this.components = new HashMap<>(this.initialComponents);
    }

    private void scanDependencies() {
        while (true) {
            List<Requirement> requirementsToFix = List.of();
            GameComponent complainingComponent = null;
            for (var component : this.components.values()) {
                if (component.requires() == null) {
                    continue;
                }
                var missingDeps = component.requires().stream().filter(r -> !r.isSatisfied(this.components, this.metadataService)).toList();
                if (!missingDeps.isEmpty()) {
                    requirementsToFix = missingDeps;
                    complainingComponent = component;
                    break;
                }
            }
            if (requirementsToFix.isEmpty()) {
                break;
            }
            try {
                adaptToRequirements(requirementsToFix);
            } catch (Exception e) {
                throw new IllegalStateException("Exception satisfying requirements for " + complainingComponent.uid() + ": " + e, e);
            }
        }
    }

    private void adaptToRequirements(List<Requirement> requirementsToFix) {
        List<ComponentCoordinate.Simple> componentsToAdd = new ArrayList<>();
        for (var r : requirementsToFix) {
            var component = this.components.get(r.uid());
            if (component != null) {
                throw new IllegalStateException("Component has requirement " + r + ", but version " + component.version() + " is already added");
            }
            String version;
            if (r.recommendedVersion().isPresent()) {
                version = r.recommendedVersion().get();
            } else if (r.uid().equals("net.fabricmc.intermediary")) {
                // Match with Minecraft version
                var minecraftComponent = this.components.get("net.minecraft");
                if (minecraftComponent == null) {
                    throw new IllegalArgumentException("Minecraft must be present to use Fabric");
                }
                version = minecraftComponent.version();
            } else {
                version = this.metadataService.getKnownVersions(r.uid()).join().getLast().version();
            }
            componentsToAdd.add(new ComponentCoordinate.Simple(r.uid(), version));
        }
        var componentObjects = componentsToAdd.stream().map(c -> this.metadataService.getComponent(c)).toList();
        CompletableFuture.allOf(componentObjects.toArray(new CompletableFuture[0])).join();
        for (var c : componentObjects) {
            var component = c.join();
            component.providedUids().forEach(uid -> this.components.put(uid, component));
        }
    }

    @Override
    public void validateRequirements(List<Requirement> requirements) throws MissingDependenciesException {
        var needed = requirements.stream().filter(r -> !r.isSatisfied(this.components, this.metadataService)).toList();
        if (!needed.isEmpty()) {
            throw new MissingDependenciesException(needed);
        }
    }

    public record Output(ReconciliationResult result, List<ReconcilableGameComponent> components) {}

    public Output runReconciliation(ReconciliationOptions options) {
        this.scanDependencies();

        Output output;
        long reconciliationStartTime = System.nanoTime();
        // Perform reconciliation
        try (ExecutorService configExecutor = Executors.newSingleThreadExecutor()) {
            do {
                List<ReconcilableGameComponent> componentsList = this.components.values().stream().distinct().sorted(Comparator.comparingInt(ReconcilableGameComponent::order)).toList();
                List<CompletableFuture<ReconciliationResult>> futures = new ArrayList<>();
                for (var component : componentsList) {
                    try {
                        futures.add(component.reconcile(this, options));
                    } catch (Throwable e) {
                        futures.add(CompletableFuture.failedFuture(e));
                    }
                }
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (Exception e) {
                    Throwable realException = e;
                    if (e instanceof CompletionException exceptionWrapper) {
                        realException = exceptionWrapper.getCause();
                        if (realException instanceof MissingDependenciesException deps) {
                            LOGGER.info("Injecting {} additional dependencies ", deps.getAdditionalDependencies().size());
                            adaptToRequirements(deps.getAdditionalDependencies());
                            scanDependencies();
                            continue;
                        }
                    }
                    throw new RuntimeException("Fatal error during reconciliation", realException);
                }
                output = new Output(ReconciliationResult.EMPTY.mergeWith(futures.stream().map(CompletableFuture::join).toList()), componentsList);
                break;
            } while (true);
        }

        long reconciliationDuration = System.nanoTime() - reconciliationStartTime;

        LOGGER.info("Reconciliation completed in {} ms", TimeUnit.NANOSECONDS.toMillis(reconciliationDuration));

        return output;
    }
}
