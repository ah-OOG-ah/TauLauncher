package org.taumc.launcher.core.mods;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ModHostingSite<M extends Mod, F extends DownloadableFile> {
    String name();

    CompletableFuture<List<M>> searchForMods(ModSearchOptions searchOptions);

    CompletableFuture<List<F>> getModFiles(M mod, ModSearchOptions searchOptions);

    CompletableFuture<List<F>> getDependencies(F file, ModSearchOptions searchOptions);

    CompletableFuture<String> getModDescriptionHTML(M mod);
}
