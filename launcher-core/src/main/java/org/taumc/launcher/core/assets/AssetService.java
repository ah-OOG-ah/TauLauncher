package org.taumc.launcher.core.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.json.Artifact;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AssetService {
    private static final Path ASSETS_FOLDER = LauncherPaths.getLauncherCache().resolve("assets");
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetService.class);

    private record AssetInfo(String hash, int size) {}

    public String getAssetsRoot() {
        return ASSETS_FOLDER.toAbsolutePath().toString();
    }

    private static final ThreadFactory DOWNLOAD_THREAD_FACTORY = Thread.ofVirtual().name("asset-downloader", 1).factory();

    public void downloadAssets(Artifact assetIndexArtifact, ProgressProvider progressProvider) throws IOException {
        String id = (String) Objects.requireNonNull(assetIndexArtifact.properties().get("id"));
        Path indexFile = ASSETS_FOLDER.resolve("indexes").resolve(id + ".json");
        if (!Files.exists(indexFile)) {
            Files.createDirectories(indexFile.getParent());
            try (Methanol client = Methanol.newBuilder().build()) {
                client.send(HttpRequest.newBuilder().uri(URI.create(assetIndexArtifact.url())).build(),
                        HttpResponse.BodyHandlers.ofFile(indexFile));
            } catch (IOException | InterruptedException e) {
                Files.deleteIfExists(indexFile);
                throw new RuntimeException("Error downloading file", e);
            }
        }
        var mapper = new ObjectMapper();
        var node = mapper.readTree(Files.newInputStream(indexFile));
        var objects = node.get("objects");
        Map<String, List<AssetInfo>> assetMap = new HashMap<>();
        objects.forEachEntry((key, assetInfo) -> {
            String hash = assetInfo.get("hash").asText();
            assetMap.computeIfAbsent(hash.substring(0, 2), $ -> new ArrayList<>()).add(new AssetInfo(hash, assetInfo.get("size").asInt()));
        });
        var objectsDir = ASSETS_FOLDER.resolve("objects");
        AtomicInteger completedAssets = new AtomicInteger(0);
        int assetTotal = 0;
        for (var entry : assetMap.entrySet()) {
            String bucketStr = entry.getKey();
            Path bucket = objectsDir.resolve(bucketStr);
            Files.createDirectories(bucket);
            Set<String> existingHashes;
            try (Stream<Path> stream = Files.find(bucket, 1, (p, a) -> a.isRegularFile())) {
                existingHashes = stream.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
            }
            entry.getValue().removeIf(e -> existingHashes.contains(e.hash()));
            assetTotal += entry.getValue().stream().mapToInt(AssetInfo::size).sum();
        }
        int finalAssetTotal = assetTotal;
        if (finalAssetTotal > 0) {
            try (Methanol client = Methanol.newBuilder().build(); var progressTask = progressProvider.addTask("Downloading assets...")) {
                Semaphore concurrencyLimiter = new Semaphore(16);
                try (ExecutorService executor = Executors.newThreadPerTaskExecutor(DOWNLOAD_THREAD_FACTORY)) {
                    for (var entry : assetMap.entrySet()) {
                        String bucketStr = entry.getKey();
                        Path bucket = objectsDir.resolve(bucketStr);
                        Files.createDirectories(bucket);
                        for (var info : entry.getValue()) {
                            executor.execute(() -> {
                                // Download the asset
                                try {
                                    concurrencyLimiter.acquire();
                                    Path targetPath = bucket.resolve(info.hash());
                                    String url = "https://resources.download.minecraft.net/" + bucketStr + "/" + info.hash();
                                    LOGGER.info("Download {}", url);
                                    client.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(targetPath));
                                    int completed = completedAssets.addAndGet(info.size());
                                    progressTask.setProgress((float)completed / finalAssetTotal);
                                } catch(IOException | InterruptedException e) {
                                    LOGGER.error("Failed to download asset", e);
                                } finally {
                                    concurrencyLimiter.release();
                                }
                            });
                        }
                    }
                }
            }
        }
    }
}
