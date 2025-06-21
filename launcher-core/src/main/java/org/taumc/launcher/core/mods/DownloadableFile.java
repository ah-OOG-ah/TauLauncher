package org.taumc.launcher.core.mods;

import org.taumc.launcher.core.meta.json.MMCPack;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface DownloadableFile {
    String downloadUrl();
    String fileName();
    ReleaseType releaseType();
    String getParentModId();
    CompletableFuture<List<? extends DownloadableFile>> getDependencies(List<MMCPack.Component> components, Set<String> existingModIds);

    enum ReleaseType {
        RELEASE,
        BETA,
        ALPHA;
    }
}
