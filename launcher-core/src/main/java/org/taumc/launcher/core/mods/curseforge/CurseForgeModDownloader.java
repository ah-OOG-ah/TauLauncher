package org.taumc.launcher.core.mods.curseforge;

import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.progress.Counter;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

public class CurseForgeModDownloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurseForgeModDownloader.class);

    private static final Map<Integer, String> CLASS_TO_SUBFOLDER = Map.of(
            12, "resourcepacks",
            17, "worlds",
            6945, "datapacks",
            6552, "shaderpacks",
            6, "mods"
    );

    private final CurseForgeAPI cfApi;
    private final CurseForgeInstanceCreator.ManualDownloadService manualDownloadService;
    private final Map<Integer, String> knownDownloadUrls;

    public CurseForgeModDownloader(CurseForgeAPI cfApi, CurseForgeInstanceCreator.ManualDownloadService manualDownloadService, Map<Integer, String> knownDownloadUrls) {
        this.cfApi = cfApi;
        this.manualDownloadService = manualDownloadService;
        this.knownDownloadUrls = knownDownloadUrls;
    }

    public CompletableFuture<Void> downloadAllMods(Methanol methanol, Path instanceFolder, List<File> fileInfos,
                                                    ProgressProvider progressProvider) {
        List<CompletableFuture<HttpResponse<Path>>> futures = new ArrayList<>();

        var modInfoTask = new Counter(fileInfos.size(), progressProvider.addTask("Fetching mod info"));
        var downloadTask = new Counter(fileInfos.stream().mapToLong(File::fileLength).sum(), progressProvider.addTask("Downloading mods"));

        Semaphore semaphore = new Semaphore(10);
        for (var file : fileInfos) {
            String downloadUrl = knownDownloadUrls.getOrDefault(file.modId(), file.downloadUrl());
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            futures.add(cfApi.getMod(file.modId()).whenComplete((c, t) -> modInfoTask.increment()).thenCompose((Function<Mod,CompletableFuture<HttpResponse<Path>>>) mod -> {
                var subfolder = CLASS_TO_SUBFOLDER.get(mod.classId());
                if (subfolder == null) {
                    return CompletableFuture.failedFuture(new RuntimeException("Unexpected class ID " + mod.classId()));
                }
                Path subfolderPath = instanceFolder.resolve(subfolder);
                try {
                    Files.createDirectories(subfolderPath);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
                Path destination = subfolderPath.resolve(file.fileName());
                if (downloadUrl == null) {
                    manualDownloadService.trackFileForManualDownload(new CurseForgeInstanceCreator.ManualDownloadService.Download(file, mod, destination));
                    LOGGER.info("File {} is missing download URL and must be downloaded manually by user", file.fileName());
                    return CompletableFuture.completedFuture(null);
                }
                var handler = HttpResponse.BodyHandlers.ofFile(destination);
                try {
                    return methanol.sendAsync(HttpRequest.newBuilder().uri(new URI(downloadUrl)).build(), handler).thenCompose(response -> {
                        if (response.statusCode() != 200) {
                            return CompletableFuture.failedFuture(new RuntimeException("Unexpected status code downloading " + file.fileName() + ": " + response.statusCode()));
                        } else {
                            return CompletableFuture.completedFuture(response);
                        }
                    }).whenComplete((c, t) -> downloadTask.increment(file.fileLength()));
                } catch (URISyntaxException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }).whenComplete((c, t) -> semaphore.release()));
        }

        var mainFuture =  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((c, t) -> {
            modInfoTask.close();
            downloadTask.close();
        });

        return mainFuture.thenCompose(f -> manualDownloadService.downloadManualFiles());
    }
}
