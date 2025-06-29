package org.taumc.launcher.core.mods.curseforge;

import org.taumc.launcher.core.mods.ModHostingSite;
import org.taumc.launcher.core.mods.ModSearchOptions;
import org.taumc.launcher.core.mods.ModUpdate;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

public class CurseForgeModHostingSite implements ModHostingSite<Mod, File> {
    public static final CurseForgeAPI API = CurseForgeAPI.INSTANCE;

    private final Map<Mod, CompletableFuture<String>> descriptionCache = new HashMap<>();

    @Override
    public String name() {
        return "CurseForge";
    }

    @Override
    public CompletableFuture<List<Mod>> searchForMods(ModSearchOptions searchOptions) {
        return API.searchForMods(searchOptions);
    }

    @Override
    public CompletableFuture<List<File>> getModFiles(Mod mod, ModSearchOptions modSearchOptions) {
        return API.getModFiles(mod.id(), modSearchOptions);
    }

    @Override
    public CompletableFuture<List<File>> getDependencies(File file, ModSearchOptions searchOptions) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<ModUpdate>> getModUpdates(List<Path> modFiles, ModSearchOptions searchOptions, ProgressProvider progressProvider) {
        var task = progressProvider.addTask("Computing mod hashes");
        var hashFutures = modFiles.stream().map(p -> CompletableFuture.supplyAsync(() -> {
            try {
                return FingerprintHasher.computeHash(p);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })).toList();
        return CompletableFuture.allOf(hashFutures.toArray(new CompletableFuture[0])).whenCompleteAsync((c, t) -> task.close()).thenComposeAsync(hashVoid -> {
            var hashes = hashFutures.stream().map(CompletableFuture::join).toList();
            return API.getFingerprintMatches(hashes).thenComposeAsync(result -> {
                if (result.exactFingerprints().size() != result.exactMatches().size()) {
                    throw new IllegalStateException("Unexpected response size mismatch in fingerprint query");
                }
                Map<Integer, Integer> hashToIndex = new HashMap<>();
                for (int i = 0; i < hashes.size(); i++) {
                    hashToIndex.put(hashes.get(i), i);
                }
                List<CompletableFuture<ModUpdate>> updates = new ArrayList<>();
                Semaphore semaphore = new Semaphore(10);
                for (int i = 0; i < result.exactFingerprints().size(); i++) {
                    var fingerprint = result.exactFingerprints().get(i);
                    var match = result.exactMatches().get(i);
                    Integer index = hashToIndex.get(fingerprint.intValue());
                    if (index == null) {
                        throw new IllegalStateException("Received hash that was never sent: " + fingerprint);
                    }
                    var originalPath = modFiles.get(index);
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    updates.add(API.getModFiles(match.file().modId(), searchOptions).whenComplete((c, t) -> semaphore.release()).thenApply(fileList -> {
                        var latest = fileList.stream().filter(f -> f.downloadUrl() != null).findFirst();
                        if (latest.isEmpty() || latest.get().id() == match.file().id()) {
                            return null;
                        }
                        return new ModUpdate(originalPath, latest.get());
                    }));
                }
                return CompletableFuture.allOf(updates.toArray(new CompletableFuture[0])).thenApply($ -> updates.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList());
            });
        });
    }

    @Override
    public CompletableFuture<String> getModDescriptionHTML(Mod mod) {
        return descriptionCache.computeIfAbsent(mod, m -> API.getModDescription(m.id()));
    }
}
