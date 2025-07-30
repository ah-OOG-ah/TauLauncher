package org.taumc.launcher.core.http;

import com.github.mizosoft.methanol.ProgressTracker;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DownloadProgressTracker {
    private static final ProgressTracker TRACKER = ProgressTracker.newBuilder().bytesTransferredThreshold(50 * 1024).build();

    public static <T> HttpResponse.BodyHandler<T> track(HttpResponse.BodyHandler<T> inner, ProgressProvider.Task task) {
        return TRACKER.tracking(inner, p -> {
            if (p.determinate()) {
                task.setProgress((float)p.value());
            }
        });
    }

    public static <T> CompletableFuture<HttpResponse<T>> trackAsync(HttpResponse.BodyHandler<T> inner,
                                                                    Function<HttpResponse.BodyHandler<T>, CompletableFuture<HttpResponse<T>>> downloader,
                                                                    ProgressProvider progressProvider,
                                                                    String taskName) {
        var task = progressProvider.addTask(taskName);
        CompletableFuture<HttpResponse<T>> future;
        try {
            future = downloader.apply(track(inner, task));
        } catch (Exception e) {
            future = CompletableFuture.failedFuture(e);
        }
        return future.whenComplete((c, t) -> task.close());
    }
}
