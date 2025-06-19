package org.taumc.launcher.core.mods.curseforge;

import org.taumc.launcher.core.mods.ModHostingSite;
import org.taumc.launcher.core.mods.ModSearchOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<String> getModDescriptionHTML(Mod mod) {
        return descriptionCache.computeIfAbsent(mod, m -> API.getModDescription(m.id()));
    }
}
