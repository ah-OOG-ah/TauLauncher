package org.taumc.launcher.core.mods;

import org.taumc.launcher.core.progress.ProgressProvider;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ModHostingSite<M extends Mod, F extends DownloadableFile> {
    default Collection<ProjectType> getProjectTypes() {
        return ProjectType.ALL_TYPES;
    }

    String name();

    CompletableFuture<List<M>> searchForMods(ModSearchOptions searchOptions);

    CompletableFuture<List<F>> getModFiles(M mod, ModSearchOptions searchOptions);

    CompletableFuture<List<F>> getDependencies(F file, ModSearchOptions searchOptions);

    CompletableFuture<List<ModUpdate>> getModUpdates(List<Path> modFiles, ModSearchOptions searchOptions, ProgressProvider progressProvider);

    CompletableFuture<String> getModDescriptionHTML(M mod);
}
