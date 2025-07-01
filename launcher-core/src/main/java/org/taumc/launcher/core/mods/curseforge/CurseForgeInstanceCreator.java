package org.taumc.launcher.core.mods.curseforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.nio.PathUtils;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.qsettings.Settings;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CurseForgeInstanceCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurseForgeInstanceCreator.class);
    private static final int MAX_CONCURRENT_DOWNLOADS = 10;

    private final CurseForgeAPI cfApi;
    private final ManualDownloadService manualDownloadService;

    public CurseForgeInstanceCreator(CurseForgeAPI cfApi, ManualDownloadService manualDownloadService) {
        this.cfApi = cfApi;
        this.manualDownloadService = manualDownloadService;
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
            var modDownloader = new CurseForgeModDownloader(cfApi, manualDownloadService, knownDownloadUrls);

            modDownloader.downloadAllMods(methanol, minecraftFolder, fileInfos, progressProvider).join();

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
            var modpackFuture = cfApi.getMod(modpackId);
            var modpackFile = cfApi.getModFile(modpackId, fileId);
            try (var task = progressProvider.addTask("Downloading modpack..."); var client = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
                var handler = DownloadProgressTracker.track(HttpResponse.BodyHandlers.ofFile(modpackZip), task);
                var response = client.send(HttpRequest.newBuilder().GET().uri(new URI(modpackFile.join().downloadUrl())).build(), handler);
                if (response.statusCode() != 200) {
                    throw new IOException("Unexpected status code retrieving modpack: " + response.statusCode());
                }
            }
            try (FileSystem zipfs = FileSystems.newFileSystem(modpackZip, Map.of("create", "false"))) {
                createInstance(target, zipfs.getRootDirectories().iterator().next(), progressProvider);
            }
            var modpack = modpackFuture.join();
            if (modpack.logo() != null && modpack.logo().url() != null) {
                String iconKey = "cf_" + modpackId;
                try (var task = progressProvider.addTask("Downloading logo..."); var client = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
                    var handler = DownloadProgressTracker.track(HttpResponse.BodyHandlers.ofFile(target.resolve(iconKey + ".png")), task);
                    client.send(HttpRequest.newBuilder().GET().uri(new URI(modpack.logo().url())).build(), handler);
                }
                var settings = new Settings();
                settings.setValue("iconKey", iconKey);
                settings.writeTo(target.resolve("instance.cfg"));
            }
        } catch (URISyntaxException | InterruptedException e) {
            throw new IOException("Unexpected error", e);
        } finally {
            Files.deleteIfExists(modpackZip);
        }
    }

    public interface ManualDownloadService {
        record Download(File file, Mod mod, Path cacheDestination, Path destination) {}

        void trackFileForManualDownload(Download download);
        CompletableFuture<Void> downloadManualFiles();
    }
}
