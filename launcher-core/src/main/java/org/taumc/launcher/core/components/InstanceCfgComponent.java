package org.taumc.launcher.core.components;

import org.taumc.launcher.core.launch.InstanceCfg;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.reconciler.ReconcilableInstance;
import org.taumc.launcher.core.reconciler.ReconciliationOptions;
import org.taumc.launcher.core.reconciler.ReconciliationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public record InstanceCfgComponent(Path path) implements ReconcilableGameComponent {
    @Override
    public int order() {
        return 10;
    }

    @Override
    public CompletableFuture<ReconciliationResult> reconcile(ReconcilableInstance instance, ReconciliationOptions options) {
        return CompletableFuture.completedFuture(ReconciliationResult.configuring(runtimeInstance -> {
            try {
                InstanceCfg.configureInstanceWithCfg(runtimeInstance, path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public String uid() {
        return "org.taumc.launcher.instance.cfg";
    }

    @Override
    public String version() {
        return "1";
    }
}
