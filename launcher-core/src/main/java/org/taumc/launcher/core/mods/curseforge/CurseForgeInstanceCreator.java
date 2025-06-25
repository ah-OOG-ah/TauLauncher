package org.taumc.launcher.core.mods.curseforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.nio.PathUtils;
import org.taumc.launcher.core.progress.Counter;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CurseForgeInstanceCreator {
    private static final Map<Integer, String> CLASS_TO_SUBFOLDER = Map.of(
            12, "resourcepacks",
            17, "worlds",
            6945, "datapacks",
            6552, "shaderpacks",
            6, "mods"
    );
    private static final Logger LOGGER = LoggerFactory.getLogger(CurseForgeInstanceCreator.class);
    private static final int MAX_CONCURRENT_DOWNLOADS = 10;

    private final CurseForgeAPI cfApi;
    private final ManualDownloadService manualDownloadService;

    public CurseForgeInstanceCreator(CurseForgeAPI cfApi, ManualDownloadService manualDownloadService) {
        this.cfApi = cfApi;
        this.manualDownloadService = manualDownloadService;
    }

    private CompletableFuture<Void> downloadAllMods(Methanol methanol, Path instanceFolder, List<File> fileInfos,
                                                    Map<Integer, String> knownDownloadUrls,
                                                    ProgressProvider progressProvider) throws IOException, URISyntaxException {
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
                    manualDownloadService.trackFileForManualDownload(new ManualDownloadService.Download(file, mod, destination));
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

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((c, t) -> {
            modInfoTask.close();
            downloadTask.close();
        });
    }

    private void generateTauLauncherConfig(Path target, PackManifest manifest) throws IOException {
        List<MMCPack.Component> components = new ArrayList<>();
        components.add(new MMCPack.Component("net.minecraft", manifest.minecraft().version()));
        Optional<PackManifest.ModLoader> loaderOptional = manifest.minecraft().modLoaders().stream().filter(PackManifest.ModLoader::primary).findFirst();
        loaderOptional.ifPresent(loader -> {
            var parts = loader.id().split("-");
            String uid = switch (parts[0]) {
                case "forge" -> "net.minecraftforge";
                case "neoforge" -> "net.neoforged";
                case "fabric" -> "net.fabricmc.fabric-loader";
                default -> throw new IllegalArgumentException(loader.id());
            };
            components.add(new MMCPack.Component(uid, parts[1]));
        });
        MMCPack pack = new MMCPack(1, components);
        try (var os = Files.newOutputStream(target.resolve("mmc-pack.json"))) {
            new ObjectMapper().writeValue(os, pack);
        }
    }

    public void createInstance(Path target, Path zipRootPath, ProgressProvider progressProvider) throws IOException {
        try (var methanol = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            PackManifest manifest;
            var manifestEntry = zipRootPath.resolve("manifest.json");
            if (!Files.exists(manifestEntry)) {
                throw new IOException("manifest.json not in modpack");
            }
            try (var is = Files.newInputStream(manifestEntry)) {
                manifest = new ObjectMapper().readValue(is, PackManifest.class);
            }
            List<File> fileInfos;
            try (var task = progressProvider.addTask("Getting file information...")) {
                fileInfos = cfApi.getFilesBulk(manifest.files().stream().map(PackManifest.File::fileID).toList()).join();
            }
            Path minecraftFolder = target.resolve("minecraft");
            Files.createDirectory(minecraftFolder);

            // Start downloading the mods
            Map<Integer, String> knownDownloadUrls = manifest.files().stream().filter(f -> f.downloadUrl() != null).collect(Collectors.toMap(PackManifest.File::fileID, PackManifest.File::downloadUrl));
            var modsDownloadFuture = downloadAllMods(methanol, minecraftFolder, fileInfos, knownDownloadUrls, progressProvider);

            modsDownloadFuture.join();

            manualDownloadService.downloadManualFiles().join();

            var overridesFolder = zipRootPath.resolve("overrides");
            try (Stream<Path> stream = Files.find(zipRootPath, Integer.MAX_VALUE, (path, attrs) -> path.startsWith(overridesFolder) && !attrs.isDirectory())) {
                stream.forEach(entry -> {
                    Path entryPath = minecraftFolder.resolve(overridesFolder.relativize(entry).toString()).normalize();

                    if (!entryPath.startsWith(minecraftFolder)) {
                        LOGGER.error("Skipping suspicious entry: {}", entry);
                        return;
                    }

                    try {
                        Files.createDirectories(entryPath.getParent());

                        try (var in = Files.newInputStream(entry)) {
                            Files.copy(in, entryPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            // Generate TauLauncher config
            generateTauLauncherConfig(target, manifest);
        } catch (Exception e) {
            PathUtils.deleteRecursively(target);
            throw new RuntimeException("Error creating instance", (e instanceof CompletionException && e.getCause() != null) ? e.getCause() : e);
        }
    }

    public void createInstance(Path target, int modpackId, int fileId, ProgressProvider progressProvider) throws IOException {
        Files.createDirectories(target);
        Path tmpDir = Files.createTempDirectory("taulauncher-modpack");
        Path modpackZip = tmpDir.resolve("modpack.zip");
        try {
            var modpackFile = cfApi.getModFile(modpackId, fileId).join();
            try (var task = progressProvider.addTask("Downloading modpack..."); var client = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
                var handler = DownloadProgressTracker.track(HttpResponse.BodyHandlers.ofFile(modpackZip), task);
                var response = client.send(HttpRequest.newBuilder().GET().uri(new URI(modpackFile.downloadUrl())).build(), handler);
                if (response.statusCode() != 200) {
                    throw new IOException("Unexpected status code retrieving modpack: " + response.statusCode());
                }
            } catch (URISyntaxException | InterruptedException e) {
                throw new IOException("Unexpected error", e);
            }
            try (FileSystem zipfs = FileSystems.newFileSystem(modpackZip, Map.of("create", "false"))) {
                createInstance(target, zipfs.getRootDirectories().iterator().next(), progressProvider);
            }
        } finally {
            Files.deleteIfExists(modpackZip);
        }
    }

    public interface ManualDownloadService {
        record Download(File file, Mod mod, Path destination) {}

        void trackFileForManualDownload(Download download);
        CompletableFuture<Void> downloadManualFiles();
    }
}
