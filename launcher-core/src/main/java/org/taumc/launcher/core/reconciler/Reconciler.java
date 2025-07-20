package org.taumc.launcher.core.reconciler;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.launch.RuntimeInstance;
import org.taumc.launcher.core.meta.component.GameComponent;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.meta.json.Requirement;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.reconciler.exceptions.MissingDependenciesException;
import org.taumc.launcher.core.reconciler.exceptions.RecoverableReconcilerException;
import org.taumc.launcher.core.reconciler.exceptions.UserInterventionRequiredException;
import org.taumc.launcher.core.reconciler.intervention.InterventionAction;
import org.taumc.launcher.core.reconciler.intervention.UserInterventionHandler;
import org.taumc.launcher.core.reconciler.tree.ComponentTreeNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Reconciler implements ReconcilableInstance {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeInstance.class);

    @Getter
    private final ComponentTreeNode componentRoot;
    private final MetadataService metadataService;
    @Getter
    private final ProgressProvider progressProvider;

    private final UserInterventionHandler interventionHandler;

    public Reconciler(ComponentTreeNode tree, MetadataService metadataService, ProgressProvider progressProvider, UserInterventionHandler interventionHandler) {
        this.metadataService = metadataService;
        this.progressProvider = progressProvider;
        this.interventionHandler = interventionHandler;
        this.componentRoot = tree.clone();
    }

    private void scanDependencies() {
        Deque<ComponentTreeNode> dependencyQueue = new ArrayDeque<>();
        dependencyQueue.add(this.componentRoot);
        Map<String, ReconcilableGameComponent> dependencyIndex = new HashMap<>(this.componentRoot.buildIndex());
        while (!dependencyQueue.isEmpty()) {
            var node = dependencyQueue.pop();
            if (node.getComponent() != null) {
                var component = node.getComponent();
                if (component.requires() == null) {
                    continue;
                }
                var unsatisfiedRequirements = new ArrayList<Requirement>();
                for (var r : component.requires()) {
                    if (!r.isSatisfied(dependencyIndex, this.metadataService)) {
                        unsatisfiedRequirements.add(r);
                    }
                }
                if (!unsatisfiedRequirements.isEmpty()) {
                    try {
                        adaptToRequirements(dependencyIndex, node, unsatisfiedRequirements);
                    } catch (Exception e) {
                        throw new IllegalStateException("Exception satisfying requirements for " + component.uid() + ": " + e, e);
                    }
                }
            }
            dependencyQueue.addAll(node.children());
        }
    }

    private void adaptToRequirements(Map<String, ReconcilableGameComponent> dependencyIndex, ComponentTreeNode node, List<Requirement> requirementsToFix) {
        List<ComponentCoordinate.Simple> componentsToAdd = new ArrayList<>();
        for (var r : requirementsToFix) {
            var component = dependencyIndex.get(r.uid());
            if (component != null) {
                throw new IllegalStateException("Component has requirement " + r + ", but version " + component.version() + " is already added");
            }
            String version;
            if (r.recommendedVersion().isPresent()) {
                version = r.recommendedVersion().get();
            } else if (r.uid().equals("net.fabricmc.intermediary")) {
                // Match with Minecraft version
                var minecraftComponent = dependencyIndex.get("net.minecraft");
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
            component.providedUids().forEach(uid -> dependencyIndex.put(uid, component));
            node.addChild(new ComponentTreeNode(component));
        }
    }

    @Override
    public void validateRequirements(List<Requirement> requirements) throws MissingDependenciesException {
        var dependencyIndex = this.componentRoot.buildIndex();
        var needed = requirements.stream().filter(r -> !r.isSatisfied(dependencyIndex, this.metadataService)).toList();
        if (!needed.isEmpty()) {
            throw new MissingDependenciesException(needed);
        }
    }

    public record Output(ReconciliationResult result, List<ReconcilableGameComponent> components) implements AutoCloseable {
        public void configureInstance(RuntimeInstance instance) {
            if (result.instanceConfigurer() != null) {
                result.instanceConfigurer().accept(instance);
            }
        }

        public Set<Path> computeUnmanagedPaths(Path instancePath) throws IOException {
            var managedPaths = result.managedPaths();
            try (Stream<Path> stream = Files.find(instancePath, Integer.MAX_VALUE, (path, attrs) -> !attrs.isDirectory())) {
                return stream.filter(p -> {
                    Path relativePath = instancePath.relativize(p);
                    return !managedPaths.containsKey(InstanceFile.fromPath(relativePath));
                }).collect(Collectors.toUnmodifiableSet());
            }
        }

        public CompletableFuture<Void> applyToFilesystem(Path instancePath) {
            long applicationStart = System.nanoTime();
            var futureList = result.managedPaths().entrySet().stream().map(entry -> CompletableFuture.runAsync(() -> {
                try {
                    Path targetPath = entry.getKey().toPath(instancePath);
                    entry.getValue().populate(targetPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })).toList();
            return CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).whenComplete((c, t) -> {
                long runtime = System.nanoTime() - applicationStart;
                LOGGER.info("Applying reconciler output to filesystem took {} ms", TimeUnit.NANOSECONDS.toMillis(runtime));
            });
        }

        @Override
        public void close() throws Exception {
            //noinspection resource
            if (result.closeFunction() != null) {
                result.closeFunction().close();
            }
        }
    }

    private static class GroupedReconciliationException extends Exception {
        private final List<Throwable> groupedThrowables;

        private GroupedReconciliationException(List<Throwable> groupedThrowables) {
            this.groupedThrowables = groupedThrowables;
        }

        public List<Throwable> unwrap() {
            return groupedThrowables.stream().flatMap(t -> {
                Throwable target = t;
                if (target instanceof TreeNodeReconciliationException tree) {
                    target = tree.getCause();
                }
                if (target instanceof GroupedReconciliationException g) {
                    return g.unwrap().stream();
                } else {
                    return Stream.of(t);
                }
            }).toList();
        }
    }

    private static class TreeNodeReconciliationException extends Exception {
        private final ComponentTreeNode thrower;

        private TreeNodeReconciliationException(Throwable cause, ComponentTreeNode thrower) {
            super(cause);
            this.thrower = thrower;
        }

        public Throwable getRealCause() {
            if (getCause() instanceof CompletionException) {
                return getCause().getCause();
            } else {
                return getCause();
            }
        }
    }

    private CompletableFuture<ReconciliationResult> buildReconciliationFuture(ReconciliationOptions options, ComponentTreeNode node) {
        try {
            var childFutures = new ArrayList<>(node.children().stream().map(child -> this.buildReconciliationFuture(options, child)).toList());
            var futuresToAwait = new ArrayList<>(childFutures);
            CompletableFuture<ReconciliationResult> parentFuture;
            if (node.getComponent() != null) {
                parentFuture = node.getComponent().reconcile(this, options).handle((result, exc) -> {
                    if (exc != null) {
                        return CompletableFuture.<ReconciliationResult>failedFuture(new TreeNodeReconciliationException(exc, node));
                    } else {
                        return CompletableFuture.completedFuture(result);
                    }
                }).thenCompose(Function.identity());
            } else {
                parentFuture = CompletableFuture.completedFuture(ReconciliationResult.EMPTY);
            }
            futuresToAwait.add(parentFuture);
            return CompletableFuture.allOf(futuresToAwait.toArray(new CompletableFuture[0])).handle((result, exc) -> {
                if (exc != null) {
                    // At least one future failed, we need to group all the exceptions and rethrow
                    var exceptions = new ArrayList<>(futuresToAwait.stream().filter(CompletableFuture::isCompletedExceptionally).map(CompletableFuture::exceptionNow).toList());
                    // Close all the results that will not be used
                    futuresToAwait.stream()
                            .filter(f -> f.isDone() && !f.isCompletedExceptionally())
                            .map(CompletableFuture::join)
                            .map(ReconciliationResult::closeFunction)
                            .filter(Objects::nonNull)
                            .forEach(fn -> {
                                try {
                                    fn.close();
                                } catch (Exception ex) {
                                    exceptions.add(ex);
                                }
                            });

                    return CompletableFuture.<ReconciliationResult>failedFuture(new GroupedReconciliationException(exceptions));
                } else {
                    return CompletableFuture.completedFuture(parentFuture.join().mergeWith(childFutures.stream().map(CompletableFuture::join).toList()));
                }
            }).thenCompose(Function.identity());
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public Output runReconciliation(ReconciliationOptions options) {
        this.scanDependencies();

        Output output;

        long reconciliationStartTime = System.nanoTime();
        // Perform reconciliation
        do {
            LOGGER.info("Executing reconciliation on tree:\n{}", this.componentRoot.toPrettyPrintedString());
            var future = this.buildReconciliationFuture(options, this.componentRoot);

            try {
                future.join();
            } catch (CompletionException completionException) {
                List<Throwable> exceptions;
                if (completionException.getCause() instanceof GroupedReconciliationException g) {
                    exceptions = g.unwrap();
                } else {
                    exceptions = new ArrayList<>();
                    exceptions.add(completionException.getCause());
                }

                List<Throwable> fatalExceptions = new ArrayList<>();

                List<InterventionAction> actions = new ArrayList<>();

                var depIndex = new HashMap<>(this.componentRoot.buildIndex());

                for (var e : exceptions) {
                    if (!(e instanceof TreeNodeReconciliationException treeNodeExc)) {
                        fatalExceptions.add(e);
                        continue;
                    }

                    var original = treeNodeExc.getRealCause();

                    if (!(original instanceof RecoverableReconcilerException recoverable)) {
                        fatalExceptions.add(original);
                        continue;
                    }

                    try {
                        switch (recoverable) {
                            case MissingDependenciesException deps -> {
                                LOGGER.info("Injecting {} additional dependencies requested by {}", deps.getAdditionalDependencies().size(), treeNodeExc.thrower.getComponent());
                                adaptToRequirements(depIndex, treeNodeExc.thrower, deps.getAdditionalDependencies());
                                scanDependencies();
                            }
                            case UserInterventionRequiredException user -> {
                                actions.addAll(user.getActions());
                            }
                        }
                    } catch (Exception recoveryException) {
                        fatalExceptions.add(recoveryException);
                    }
                }

                if (!fatalExceptions.isEmpty()) {
                    var finalException = new RuntimeException("Fatal error during reconciliation", fatalExceptions.getFirst());
                    fatalExceptions.stream().skip(1).forEach(finalException::addSuppressed);
                    throw finalException;
                }

                if (!actions.isEmpty()) {
                    this.interventionHandler.awaitUserIntervention(actions);
                }

                continue;
            }
            output = new Output(future.join(), this.componentRoot.buildIndex().values().stream().distinct().sorted(Comparator.comparingInt(ReconcilableGameComponent::order)).toList());
            break;
        } while (true);

        long reconciliationDuration = System.nanoTime() - reconciliationStartTime;

        LOGGER.info("Reconciliation completed in {} ms", TimeUnit.NANOSECONDS.toMillis(reconciliationDuration));

        return output;
    }
}
