package org.taumc.launcher.core.meta.json;

import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;

import java.util.concurrent.CompletableFuture;

public interface ComponentRetriever {
    CompletableFuture<ReconcilableGameComponent> retrieveComponent(String pkgName, String version);
}
