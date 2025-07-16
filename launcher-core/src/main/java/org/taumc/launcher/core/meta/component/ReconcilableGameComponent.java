package org.taumc.launcher.core.meta.component;

import org.taumc.launcher.core.launch.RuntimeInstance;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface ReconcilableGameComponent extends GameComponent {
    int order();
    CompletableFuture<Void> reconcile(RuntimeInstance instance, Executor configurationExecutor);
}
