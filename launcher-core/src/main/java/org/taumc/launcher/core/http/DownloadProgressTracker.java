package org.taumc.launcher.core.http;

import com.github.mizosoft.methanol.ProgressTracker;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.net.http.HttpResponse;

public class DownloadProgressTracker {
    private static final ProgressTracker TRACKER = ProgressTracker.newBuilder().bytesTransferredThreshold(50 * 1024).build();

    public static <T> HttpResponse.BodyHandler<T> track(HttpResponse.BodyHandler<T> inner, ProgressProvider.Task task) {
        return TRACKER.tracking(inner, p -> {
            if (p.determinate()) {
                task.setProgress((float)p.value());
            }
        });
    }
}
