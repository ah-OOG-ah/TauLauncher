package org.taumc.launcher.core.meta.json;

import org.jetbrains.annotations.ApiStatus;
import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.ComponentSearchQuery;
import org.taumc.launcher.core.meta.component.ComponentSearchResults;
import org.taumc.launcher.core.meta.component.GameComponent;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.util.FutureUtils;

import java.io.Closeable;
import java.util.Collections;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface MetaRepository extends Closeable {
    default boolean couldHavePackage(String uid) {
        return true;
    }

    default boolean couldHaveVersion(String uid, String version) {
        return true;
    }

    default CompletableFuture<Set<String>> getKnownPackages() {
        return CompletableFuture.completedFuture(Set.of());
    }

    @ApiStatus.NonExtendable
    default CompletableFuture<SequencedCollection<? extends GameComponent>> getKnownVersions(String pkgName) {
        return getKnownVersions(pkgName, ComponentSearchQuery.ALL);
    }

    default CompletableFuture<SequencedCollection<? extends GameComponent>> getKnownVersions(String pkgName, ComponentSearchQuery searchQuery) {
        return CompletableFuture.completedFuture(Collections.emptySortedSet());
    }

    CompletableFuture<ComponentMetaInfo> retrieveComponentMeta(String pkgName);

    default CompletableFuture<ComponentSearchResults> search(ComponentSearchQuery searchQuery) {
        return getKnownPackages().thenCompose(packages -> {
            return FutureUtils.allOfWithResults(packages.stream().map(pkg -> {
                var metaFuture = retrieveComponentMeta(pkg);
                return metaFuture.thenApply(meta -> new ComponentSearchResults.Result(pkg, meta));
            }).toList()).thenApply(list -> list.stream().filter(Objects::nonNull).toList());
        }).thenApply(ComponentSearchResults::new);
    }

    CompletableFuture<ReconcilableGameComponent> retrieveComponent(String pkgName, String version);

    void close();
}
