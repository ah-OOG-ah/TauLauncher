package org.taumc.launcher.core.mods.modpacksch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.mods.DownloadableFile;
import org.taumc.launcher.core.mods.Mod;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Modpack(String name, int id, int installs, String synopsis, String description, List<ArtItem> art, List<VersionDescriptor> versions) implements Mod {
    @Override
    public String modId() {
        return String.valueOf(id);
    }

    @Override
    public String summary() {
        return synopsis;
    }

    @Override
    public String smallIconUrl() {
        return art.stream().filter(a -> "square".equals(a.type())).findFirst().map(ArtItem::url).orElse(null);
    }

    @Override
    public int downloadCount() {
        return installs;
    }

    public List<DownloadableModpackVersion> getDownloadableFiles() {
        return versions.stream().map(v -> new DownloadableModpackVersion(this, v)).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtItem(int size, int width, int height, String url, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VersionDescriptor(int id, String name, long updated) {}
}
