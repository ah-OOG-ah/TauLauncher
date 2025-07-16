package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.With;
import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.GameComponent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PackageIndex(String name, String uid, List<Version> versions, Metadata tauMetadata) {
    private static final Set<String> TOP_LEVEL_COMPONENTS = Set.of("net.minecraft", "net.minecraftforge", "net.fabricmc.fabric-loader", "net.neoforged");

    @JsonCreator
    public PackageIndex {
        if (versions != null) {
            // Inject UID into each Version
            versions = versions.stream()
                    .map(v -> v.withUid(uid))
                    .toList();
        }
    }

    public record Metadata(boolean isUserInstallable) {}

    public Optional<Version> version(String version) {
        return versions.stream().filter(v -> version.equals(v.version)).findFirst();
    }

    public ComponentMetaInfo getMetadata() {
        return new ComponentMetaInfo() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean isUserInstallable() {
                return tauMetadata != null ? tauMetadata.isUserInstallable() : TOP_LEVEL_COMPONENTS.contains(uid);
            }
        };
    }

    public record Version(
            boolean recommended,
            String releaseTime,
            @With List<Requirement> requires,
            String version,
            String sha256,
            @JsonAnySetter Map<String, Object> properties,
            @With(AccessLevel.PACKAGE) String uid
    ) implements GameComponent {
        public static Version simple(String version, List<Requirement> requires) {
            return new Version(false, null, requires, version, null, null, null);
        }
    }
}
