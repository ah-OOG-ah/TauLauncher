package org.taumc.launcher.core.mods.curseforge;

import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.progress.Counter;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.storage.LauncherPaths;

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

    private static final Path RESOURCE_CACHE = LauncherPaths.getLauncherCache().resolve("caches").resolve("curseforge").resolve("modfiles");

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
        try {
            Files.createDirectories(RESOURCE_CACHE);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();

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
            futures.add(cfApi.getMod(file.modId()).whenComplete((c, t) -> modInfoTask.increment()).thenCompose(mod -> {
                var subfolder = CLASS_TO_SUBFOLDER.get(mod.classId());
                if (subfolder == null) {
                    return CompletableFuture.failedFuture(new RuntimeException("Unexpected class ID " + mod.classId()));
                }
                Path subfolderPath = instanceFolder.resolve(subfolder);
                Path destination = subfolderPath.resolve(file.fileName());
                Path cachedResource = RESOURCE_CACHE.resolve(String.valueOf(file.modId())).resolve(String.valueOf(file.id()));

                boolean needPopulateCache = false;
                try {
                    Files.createDirectories(subfolderPath);
                    if (!Files.exists(cachedResource) || Files.size(cachedResource) != file.fileLength()) {
                        Files.createDirectories(cachedResource.getParent());
                        Files.deleteIfExists(cachedResource);
                        needPopulateCache = true;
                    }
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }

                if (needPopulateCache && downloadUrl == null) {
                    manualDownloadService.trackFileForManualDownload(new CurseForgeInstanceCreator.ManualDownloadService.Download(file, mod, cachedResource, destination));
                    LOGGER.info("File {} is missing download URL and must be downloaded manually by user", file.fileName());
                    return CompletableFuture.completedFuture(null);
                }

                CompletableFuture<?> downloadToCacheFuture;
                if (needPopulateCache) {
                    var handler = HttpResponse.BodyHandlers.ofFile(cachedResource);
                    try {
                        downloadToCacheFuture = methanol.sendAsync(HttpRequest.newBuilder().uri(new URI(downloadUrl)).build(), handler).thenCompose(response -> {
                            if (response.statusCode() != 200) {
                                return CompletableFuture.failedFuture(new RuntimeException("Unexpected status code downloading " + file.fileName() + ": " + response.statusCode()));
                            } else {
                                return CompletableFuture.completedFuture(response);
                            }
                        });
                    } catch (URISyntaxException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                } else {
                    LOGGER.info("Reusing cached download of {} (ID {}) version {} (ID {})", mod.name(), mod.id(), file.displayName(), file.id());
                    downloadToCacheFuture = CompletableFuture.completedFuture(null);
                }
                return downloadToCacheFuture.thenRunAsync(() -> {
                        try {
                            Files.copy(cachedResource, destination);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                }).whenComplete((c, t) -> downloadTask.increment(file.fileLength()));
            }).whenComplete((c, t) -> semaphore.release()));
        }

        var mainFuture =  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((c, t) -> {
            modInfoTask.close();
            downloadTask.close();
        });

        return mainFuture.thenCompose(f -> manualDownloadService.downloadManualFiles());
    }
}
