package org.taumc.launcher.core.mods.curseforge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.mods.DownloadableFile;
import org.taumc.launcher.core.mods.ModSearchOptions;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@JsonIgnoreProperties(ignoreUnknown = true)
public record File(int id, int gameId, int modId, boolean isAvailable, String downloadUrl, String displayName, String fileName, Instant fileDate, List<Dependency> dependencies, @JsonProperty("releaseType") int cfReleaseType) implements DownloadableFile {
    public record Dependency(int modId, int relationType) {}

    @Override
    public ReleaseType releaseType() {
        var values = FileReleaseType.values();
        if (cfReleaseType >= 0 && cfReleaseType < values.length) {
            return switch (values[cfReleaseType]) {
                case BETA -> ReleaseType.BETA;
                case ALPHA -> ReleaseType.ALPHA;
                default -> ReleaseType.RELEASE;
            };
        }
        return ReleaseType.RELEASE;
    }

    @Override
    public String getParentModId() {
        return String.valueOf(modId);
    }

    @Override
    public CompletableFuture<List<? extends DownloadableFile>> getDependencies(List<MMCPack.Component> components, Set<String> existingModIds) {
        if (dependencies == null || dependencies.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        var modSearchOptions = new ModSearchOptions();
        modSearchOptions.componentFilter = components;
        List<CompletableFuture<Optional<File>>> dependencyFutures = dependencies.stream()
                .filter(d -> !existingModIds.contains(d.modId()))
                .filter(d -> d.relationType() == 3)
                .map(d -> {
                    return CurseForgeAPI.INSTANCE.getModFiles(d.modId, modSearchOptions).thenApply(fileList -> {
                        return fileList.isEmpty() ? Optional.<File>empty() : Optional.of(fileList.getFirst());
                    });
                })
                .toList();
        return CompletableFuture.allOf(dependencyFutures.toArray(CompletableFuture[]::new))
                .thenApply($ -> dependencyFutures.stream().map(CompletableFuture::join).flatMap(Optional::stream).toList());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        File file = (File) o;
        return id == file.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
