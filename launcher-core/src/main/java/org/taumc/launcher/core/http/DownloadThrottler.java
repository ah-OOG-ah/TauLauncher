package org.taumc.launcher.core.http;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public class DownloadThrottler {
    private final Semaphore semaphore = new Semaphore(10);

    public <T> CompletableFuture<T> throttle(Supplier<CompletableFuture<T>> supplier) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return supplier.get().whenComplete((c, t) -> semaphore.release());
    }
}
