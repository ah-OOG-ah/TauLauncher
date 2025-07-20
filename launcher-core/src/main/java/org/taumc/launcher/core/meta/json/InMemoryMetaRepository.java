package org.taumc.launcher.core.meta.json;

import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.ComponentSearchQuery;
import org.taumc.launcher.core.meta.component.GameComponent;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class InMemoryMetaRepository implements MetaRepository {
    private final Map<String, PackageIndex> packageIndexes = new HashMap<>();

    private CompletableFuture<Void> initializationFuture;

    protected final void addPackage(PackageIndex index) {
        packageIndexes.put(index.uid(), index);
    }

    @Override
    public boolean couldHavePackage(String uid) {
        getInitializationFuture().join();
        return packageIndexes.containsKey(uid);
    }

    protected abstract void populateRepository() throws IOException;

    private synchronized CompletableFuture<Void> getInitializationFuture() {
        if (initializationFuture == null) {
            initializationFuture = CompletableFuture.runAsync(() -> {
                try {
                    populateRepository();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return initializationFuture;
    }

    @Override
    public CompletableFuture<Set<String>> getKnownPackages() {
        return getInitializationFuture().thenApply($ -> packageIndexes.keySet());
    }

    @Override
    public CompletableFuture<SequencedCollection<? extends GameComponent>> getKnownVersions(String pkgName, ComponentSearchQuery searchQuery) {
        return getInitializationFuture().thenApply($ -> {
            var idx = packageIndexes.get(pkgName);
            if (idx == null) {
                return Collections.emptySortedSet();
            } else {
                return idx.versions();
            }
        });
    }

    public final CompletableFuture<PackageIndex> getPackageIndex(String pkgName) {
        return getInitializationFuture().thenApply($ -> Objects.requireNonNull(packageIndexes.get(pkgName)));
    }

    @Override
    public CompletableFuture<ComponentMetaInfo> retrieveComponentMeta(String pkgName) {
        return getPackageIndex(pkgName).thenApply(Function.identity());
    }

    @Override
    public void close() {

    }
}
