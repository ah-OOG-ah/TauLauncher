package org.taumc.launcher.core.meta.component;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface ComponentMetaInfo {
    String name();
    default String summary() {
        return "";
    }
    boolean isUserInstallable();

    default CompletableFuture<String> description() {
        return CompletableFuture.completedFuture("");
    }

    default @Nullable String logoUrl() {
        return null;
    }
}
