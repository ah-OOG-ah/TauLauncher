package org.taumc.launcher.core.meta.json;

import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.GameComponent;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;

import java.io.Closeable;
import java.util.Collections;
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

    default CompletableFuture<SequencedCollection<? extends GameComponent>> getKnownVersions(String pkgName) {
        return CompletableFuture.completedFuture(Collections.emptySortedSet());
    }

    CompletableFuture<ComponentMetaInfo> retrieveComponentMeta(String pkgName);

    CompletableFuture<ReconcilableGameComponent> retrieveComponent(String pkgName, String version);

    void close();
}
