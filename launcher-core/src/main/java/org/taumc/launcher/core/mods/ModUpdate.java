package org.taumc.launcher.core.mods;

import com.github.mizosoft.methanol.Methanol;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public record ModUpdate(Path originalFile, DownloadableFile update) {
    public static CompletableFuture<Void> applyUpdates(List<ModUpdate> updatesToApply, ProgressProvider progressProvider) {
        if (updatesToApply.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        var client = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (var update : updatesToApply) {
            var file = update.update();
            try {
                URI uri = new URI(file.downloadUrl());
                var task = progressProvider.addTask(file.fileName());
                Path destination = update.originalFile().getParent().resolve(file.fileName()).normalize();
                if (!destination.startsWith(update.originalFile().getParent())) {
                    throw new IOException("Invalid file name");
                }
                var handler = DownloadProgressTracker.track(HttpResponse.BodyHandlers.ofFile(destination), task);
                futures.add(client.sendAsync(HttpRequest.newBuilder().uri(uri).build(), handler).thenRun(() -> {
                    try {
                        Files.delete(update.originalFile());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).whenComplete((c, t) -> {
                    task.close();
                }));
            } catch (Exception e) {
                futures.add(CompletableFuture.failedFuture(e));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((c, t) -> client.close());
    }
}
