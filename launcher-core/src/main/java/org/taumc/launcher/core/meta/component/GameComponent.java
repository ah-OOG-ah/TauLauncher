package org.taumc.launcher.core.meta.component;

import org.taumc.launcher.core.launch.RuntimeInstance;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.Requirement;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface GameComponent extends ComponentCoordinate {
    List<Requirement> requires();
    CompletableFuture<Void> reconcile(RuntimeInstance instance, Executor configurationExecutor);
    int order();
}
