package org.taumc.launcher.core.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.taumc.launcher.core.meta.json.JsonDecoder;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ResourceCache {
    private static final ObjectMapper MAPPER = JsonDecoder.make();
    private final Path cacheFolder;

    public ResourceCache(String key) {
        this.cacheFolder = LauncherPaths.getLauncherCache().resolve("caches").resolve(key);
    }

    public CompletableFuture<Path> computeIfAbsent(String key, Function<Path, CompletableFuture<?>> supplier) {
        return computeIfAbsent(key, p -> true, supplier);
    }

    public interface EntryVerifier {
        boolean isEntryValid(Path path) throws IOException;
    }

    public CompletableFuture<Path> computeIfAbsent(String key, EntryVerifier entryVerifier, Function<Path, CompletableFuture<?>> supplier) {
        Path path = cacheFolder.resolve(key);
        try {
            if (Files.exists(path) && entryVerifier.isEntryValid(path)) {
                return CompletableFuture.completedFuture(path);
            } else {
                Files.createDirectories(path.getParent());
                Files.deleteIfExists(path);
                return supplier.apply(path).thenApply($ -> path);
            }
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public <T> CompletableFuture<T> computeIfAbsent(String key, Class<T> objectType, Supplier<CompletableFuture<T>> supplier) {
        return computeIfAbsent(key, destination -> {
            return supplier.get().thenApplyAsync(obj -> {
                if (!objectType.isInstance(obj)) {
                    throw new IllegalArgumentException();
                }
                try (var os = Files.newOutputStream(destination)) {
                    MAPPER.writeValue(os, obj);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return destination;
            });
        }).thenApplyAsync(path -> {
            try (var is = Files.newInputStream(path)) {
                return MAPPER.readValue(is, objectType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
