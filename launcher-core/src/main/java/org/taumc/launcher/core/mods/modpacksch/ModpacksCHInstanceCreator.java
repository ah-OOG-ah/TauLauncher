package org.taumc.launcher.core.mods.modpacksch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.http.JacksonBodyHandler;
import org.taumc.launcher.core.meta.json.JsonDecoder;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.mods.curseforge.CurseForgeAPI;
import org.taumc.launcher.core.mods.curseforge.CurseForgeInstanceCreator;
import org.taumc.launcher.core.mods.curseforge.CurseForgeModDownloader;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class ModpacksCHInstanceCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModpacksCHInstanceCreator.class);

    private final CurseForgeInstanceCreator.ManualDownloadService manualDownloadService;

    public ModpacksCHInstanceCreator(CurseForgeInstanceCreator.ManualDownloadService manualDownloadService) {
        this.manualDownloadService = manualDownloadService;
    }

    private void generateTauLauncherConfig(Path instancePath, ModpackVersion manifest) throws IOException {
        List<MMCPack.Component> components = new ArrayList<>();
        for (var target : manifest.targets()) {
            String uid = switch (target.name()) {
                case "minecraft" -> "net.minecraft";
                case "neoforge" -> "net.neoforged";
                case "forge" -> "net.minecraftforge";
                case "java" -> "net.adoptium.java";
                default -> throw new IllegalArgumentException("Unexpected target: " + target);
            };
            String version = target.version();
            if (target.name().equals("java")) {
                continue;
            }
            components.add(new MMCPack.Component(uid, version));
        }
        MMCPack pack = new MMCPack(1, components);
        try (var os = Files.newOutputStream(instancePath.resolve("mmc-pack.json"))) {
            new ObjectMapper().writeValue(os, pack);
        }
    }

    public void createInstance(Path target, ModpackVersion manifest, ProgressProvider progressProvider) throws IOException {
        Files.createDirectories(target);
        Path minecraftFolder = target.resolve("minecraft");
        Files.createDirectory(minecraftFolder);

        var relevantFiles = manifest.files().stream().filter(f -> f.clientonly() || !f.serveronly()).toList();

        var modDownloader = new CurseForgeModDownloader(CurseForgeAPI.INSTANCE, this.manualDownloadService, Map.of());

        var cfFileIds = relevantFiles.stream().filter(f -> f.curseforge() != null).map(f -> f.curseforge().file()).toList();
        var manualFiles = relevantFiles.stream().filter(f -> f.url() != null && !f.url().isBlank()).toList();

        try (Methanol client = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            var fileList = CurseForgeAPI.INSTANCE.getFilesBulk(cfFileIds).join();
            modDownloader.downloadAllMods(client, minecraftFolder, fileList, progressProvider).join();

            Semaphore downloadLock = new Semaphore(10);

            long totalSize = manualFiles.stream().mapToLong(ModpackVersion.File::size).sum();
            AtomicLong downloadedSize = new AtomicLong(0);

            try (var task = progressProvider.addTask("Downloading content")) {
                List<CompletableFuture<HttpResponse<Path>>> futures = new ArrayList<>();

                for (var file : manualFiles) {
                    Path destination = minecraftFolder.resolve(file.path()).resolve(file.name()).normalize();
                    if (!destination.startsWith(minecraftFolder)) {
                        throw new IOException("Illegal path " + file.path());
                    }

                    Files.createDirectories(destination.getParent());

                    var handler = HttpResponse.BodyHandlers.ofFile(destination);

                    URI uri;
                    try {
                        uri = new URI(file.url().replace(" ", "%20"));
                    } catch (URISyntaxException e) {
                        futures.add(CompletableFuture.failedFuture(e));
                        continue;
                    }
                    downloadLock.acquire();
                    LOGGER.info("Downloading {}", file.path() + "/" + file.name());
                    try {
                        futures.add(client.sendAsync(HttpRequest.newBuilder().uri(uri).build(), handler).whenComplete((c, t) -> downloadLock.release()).thenCompose(response -> {
                            if (response.statusCode() != 200) {
                                return CompletableFuture.failedFuture(new RuntimeException("Unexpected status code downloading " + file.name() + ": " + response.statusCode()));
                            } else {
                                task.setProgress((float)((double)downloadedSize.addAndGet(file.size()) / totalSize));
                                return CompletableFuture.completedFuture(response);
                            }
                        }));
                    } catch (RuntimeException e) {
                        downloadLock.release();
                        futures.add(CompletableFuture.failedFuture(new Exception("Error downloading file " + file, e)));
                    }
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            generateTauLauncherConfig(target, manifest);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    public void createInstance(Path target, DownloadableModpackVersion downloadableModpackVersion, ProgressProvider progressProvider) throws IOException {
        ModpackVersion manifest;
        try (Methanol client = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(); var task = progressProvider.addTask("Downloading modpack manifest")) {
            var handler = DownloadProgressTracker.track(new JacksonBodyHandler<>(new TypeReference<ModpackVersion>() {}, JsonDecoder.make()), task);
            LOGGER.info("Downloading modpack manifest from {}", downloadableModpackVersion.downloadUrl());
            manifest = client.send(HttpRequest.newBuilder().uri(new URI(downloadableModpackVersion.downloadUrl())).build(), handler).body();
        } catch (InterruptedException | URISyntaxException e) {
            throw new IOException("Interrupted", e);
        }
        createInstance(target, manifest, progressProvider);
    }
}
