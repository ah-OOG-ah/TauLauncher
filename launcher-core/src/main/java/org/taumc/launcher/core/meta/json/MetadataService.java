package org.taumc.launcher.core.meta.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.GameComponent;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;
import org.taumc.launcher.core.util.FutureUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MetadataService implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataService.class);

    private final List<MetaRepository> repositories;
    private final Set<String> knownPackages = new HashSet<>();
    private final Map<String, CompletableFuture<SequencedSet<GameComponent>>> knownVersionsCache = new ConcurrentHashMap<>();
    private final Map<ComponentCoordinate.Simple, CompletableFuture<ReconcilableGameComponent>> componentCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<ComponentMetaInfo>> infoCache = new ConcurrentHashMap<>();

    private boolean indexed = false;

    public MetadataService() {
        this(List.of());
    }

    public MetadataService(List<MetaRepository> repositories) {
        this.repositories = new ArrayList<>(repositories);
    }

    private synchronized void checkIndexed() {
        if (!this.indexed) {
            this.knownPackages.clear();
            this.knownPackages.addAll(this.repositories.parallelStream().flatMap(r -> r.getKnownPackages().join().stream()).collect(Collectors.toUnmodifiableSet()));
            this.indexed = true;
        }
    }

    public List<MetaRepository> getRepositories() {
        return Collections.unmodifiableList(this.repositories);
    }

    public void addRepository(MetaRepository repository) {
        this.repositories.add(repository);
        synchronized (this) {
            this.indexed = false;
        }
    }

    public void addRepositoryFirst(MetaRepository repository) {
        this.repositories.addFirst(repository);
        synchronized (this) {
            this.indexed = false;
        }
    }

    public Set<String> getKnownPackages() {
        this.checkIndexed();
        return Collections.unmodifiableSet(this.knownPackages);
    }

    public CompletableFuture<SequencedSet<GameComponent>> getKnownVersions(String pkg) {
        return this.knownVersionsCache.computeIfAbsent(pkg, uid -> {
            var futureList = this.repositories.stream().map(r -> r.getKnownVersions(uid)).toList();
            return CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).thenApply($ -> {
                var versionsSet = new LinkedHashSet<GameComponent>();
                for (var f : futureList) {
                    versionsSet.addAll(f.join());
                }
                return versionsSet;
            });
        });
    }

    public final CompletableFuture<ReconcilableGameComponent> getComponent(ComponentCoordinate coordinate) {
        if (coordinate instanceof ReconcilableGameComponent component) {
            return CompletableFuture.completedFuture(component);
        }
        return getComponent(coordinate.uid(), coordinate.version());
    }

    private CompletableFuture<ReconcilableGameComponent> findComponent(String pkg, String version) {
        return FutureUtils.anySuccessful(this.repositories.stream().filter(r -> r.couldHavePackage(pkg) && r.couldHaveVersion(pkg, version)).map(r -> r.retrieveComponent(pkg, version)).toList());
    }

    public CompletableFuture<ReconcilableGameComponent> getComponent(String pkg, String version)  {
        this.checkIndexed();

        var key = new ComponentCoordinate.Simple(pkg, version);
        var existing = componentCache.get(key);
        if (existing != null) {
            return existing;
        }

        existing = findComponent(pkg, version);

        if (existing != null) {
            componentCache.put(key, existing);
        }

        return existing;
    }

    public CompletableFuture<ComponentMetaInfo> getComponentMeta(String pkg) {
        return this.infoCache.computeIfAbsent(pkg, uid -> {
            return FutureUtils.anySuccessful(this.repositories.stream().filter(r -> r.couldHavePackage(uid)).map(r -> r.retrieveComponentMeta(pkg)).toList());
        });
    }

    @Override
    public void close() {
        this.repositories.forEach(MetaRepository::close);
        this.repositories.clear();
    }
}
