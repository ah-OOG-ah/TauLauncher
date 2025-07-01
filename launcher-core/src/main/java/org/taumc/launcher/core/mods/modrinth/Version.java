package org.taumc.launcher.core.mods.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.mods.DownloadableFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Version(String name, String version_number, List<String> game_versions, Instant date_published, String version_type, String id, String project_id, int downloads, List<File> files) implements DownloadableFile {
    private File getPrimaryFile() {
        if (files.size() == 1) {
            return files.getFirst();
        }
        for (var f : files) {
            if (f.primary()) {
                return f;
            }
        }
        throw new IllegalStateException();
    }

    @Override
    public String downloadUrl() {
        return getPrimaryFile().url;
    }

    @Override
    public String fileName() {
        return getPrimaryFile().filename;
    }

    @Override
    public Instant releaseTime() {
        return date_published;
    }

    @Override
    public ReleaseType releaseType() {
        return switch (version_type) {
            case "beta" -> ReleaseType.BETA;
            case "alpha" -> ReleaseType.ALPHA;
            default -> ReleaseType.RELEASE;
        };
    }

    @Override
    public String getParentModId() {
        return project_id;
    }

    @Override
    public CompletableFuture<List<? extends DownloadableFile>> getDependencies(List<MMCPack.Component> components, Set<String> existingModIds) {
        return CompletableFuture.completedFuture(List.of());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record File(Map<String, String> hashes, String url, String filename, boolean primary, int size) {}
}
