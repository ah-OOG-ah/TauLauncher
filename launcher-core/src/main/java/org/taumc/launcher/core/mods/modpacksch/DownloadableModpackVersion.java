package org.taumc.launcher.core.mods.modpacksch;

import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.mods.DownloadableFile;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public record DownloadableModpackVersion(String name, String modpackName, int parentId, int id, long updated) implements DownloadableFile {
    public DownloadableModpackVersion(Modpack modpack, Modpack.VersionDescriptor descriptor) {
        this(descriptor.name(), modpack.name(), modpack.id(), descriptor.id(), descriptor.updated());
    }

    @Override
    public String downloadUrl() {
        return "https://api.modpacks.ch/public/modpack/" + parentId + "/" + id;
    }

    @Override
    public String fileName() {
        return name;
    }

    @Override
    public ReleaseType releaseType() {
        return ReleaseType.RELEASE;
    }

    @Override
    public Instant releaseTime() {
        return Instant.ofEpochSecond(updated);
    }

    @Override
    public String getParentModId() {
        return String.valueOf(parentId);
    }

    @Override
    public CompletableFuture<List<? extends DownloadableFile>> getDependencies(List<MMCPack.Component> components, Set<String> existingModIds) {
        return CompletableFuture.completedFuture(List.of());
    }
}
