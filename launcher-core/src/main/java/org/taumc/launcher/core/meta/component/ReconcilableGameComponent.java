package org.taumc.launcher.core.meta.component;

import org.taumc.launcher.core.launch.RuntimeInstance;
import org.taumc.launcher.core.reconciler.ReconciliationOptions;
import org.taumc.launcher.core.reconciler.ReconciliationResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface ReconcilableGameComponent extends GameComponent {
    int order();
    CompletableFuture<ReconciliationResult> reconcile(RuntimeInstance instance, Executor configurationExecutor, ReconciliationOptions options);
}
