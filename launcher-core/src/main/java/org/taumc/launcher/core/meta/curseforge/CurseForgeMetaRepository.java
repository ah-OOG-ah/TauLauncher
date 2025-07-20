package org.taumc.launcher.core.meta.curseforge;

import org.taumc.launcher.core.cache.ResourceCache;
import org.taumc.launcher.core.http.DownloadThrottler;
import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.ComponentSearchQuery;
import org.taumc.launcher.core.meta.component.ComponentSearchResults;
import org.taumc.launcher.core.meta.component.GameComponent;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.MetaRepository;
import org.taumc.launcher.core.mods.curseforge.CurseForgeAPI;
import org.taumc.launcher.core.mods.curseforge.File;
import org.taumc.launcher.core.mods.curseforge.Mod;

import java.io.IOException;
import java.util.OptionalInt;
import java.util.SequencedCollection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Pattern;

public class CurseForgeMetaRepository implements MetaRepository {
    private static final Pattern CURSEFORGE_UID_PATTERN = Pattern.compile("com\\.curseforge\\.projects\\.([0-9]+)");
    private static final ResourceCache META_CACHE = new ResourceCache("curseforge_meta");
    public static final DownloadThrottler THROTTLER = new DownloadThrottler();

    public static String getUidFromProjectId(int id) {
        return "com.curseforge.projects." + id;
    }

    public static OptionalInt getProjectId(String pkgName) {
        var matcher = CURSEFORGE_UID_PATTERN.matcher(pkgName);
        if (matcher.matches()) {
            return OptionalInt.of(Integer.parseInt(matcher.group(1)));
        } else {
            return OptionalInt.empty();
        }
    }

    private static <T> CompletableFuture<T> failedFuture() {
        return CompletableFuture.failedFuture(new IOException("Not a CurseForge project"));
    }

    private CompletableFuture<Mod> getMod(int id) {
        return META_CACHE.computeIfAbsent(String.valueOf(id), Mod.class, () -> THROTTLER.throttle(() -> CurseForgeAPI.INSTANCE.getMod(id)));
    }

    @Override
    public CompletableFuture<ComponentMetaInfo> retrieveComponentMeta(String pkgName) {
        var projectId = getProjectId(pkgName);
        if (!projectId.isPresent()) {
            return failedFuture();
        }
        return getMod(projectId.getAsInt()).thenApply(Function.identity());
    }

    @Override
    public CompletableFuture<ReconcilableGameComponent> retrieveComponent(String pkgName, String version) {
        var projectId = getProjectId(pkgName);
        if (!projectId.isPresent()) {
            return failedFuture();
        }
        int fileId = Integer.parseInt(version);
        return META_CACHE.computeIfAbsent(projectId.getAsInt() + "_" + fileId, File.class, () -> THROTTLER.throttle(() -> CurseForgeAPI.INSTANCE.getModFile(projectId.getAsInt(), fileId))).thenComposeAsync(file -> {
            return getMod(projectId.getAsInt()).thenApply(mod -> new CurseForgeModComponent(mod, file));
        });
    }

    @Override
    public CompletableFuture<SequencedCollection<? extends GameComponent>> getKnownVersions(String pkgName, ComponentSearchQuery searchQuery) {
        var projectId = getProjectId(pkgName);
        if (!projectId.isPresent()) {
            return failedFuture();
        }
        var mod = getMod(projectId.getAsInt());
        return CurseForgeAPI.INSTANCE.getModFiles(projectId.getAsInt(), searchQuery).thenApply(fileList -> {
            return fileList.stream().map(file -> new CurseForgeModComponent(mod.join(), file)).toList();
        });
    }

    @Override
    public CompletableFuture<ComponentSearchResults> search(ComponentSearchQuery searchQuery) {
        return CurseForgeAPI.INSTANCE.searchForMods(searchQuery).thenApply(modList -> {
            return new ComponentSearchResults(modList.stream().map(mod -> new ComponentSearchResults.Result(getUidFromProjectId(mod.id()), mod)).toList());
        });
    }

    @Override
    public void close() {

    }

    @Override
    public String toString() {
        return "CurseForge";
    }
}
