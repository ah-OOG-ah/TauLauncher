package org.taumc.launcher.core.meta.component;

import org.taumc.launcher.core.reconciler.ReconcilableInstance;
import org.taumc.launcher.core.reconciler.ReconciliationOptions;
import org.taumc.launcher.core.reconciler.ReconciliationResult;

import java.util.concurrent.CompletableFuture;

public interface ReconcilableGameComponent extends GameComponent {
    int order();
    CompletableFuture<ReconciliationResult> reconcile(ReconcilableInstance instance, ReconciliationOptions options);
}
