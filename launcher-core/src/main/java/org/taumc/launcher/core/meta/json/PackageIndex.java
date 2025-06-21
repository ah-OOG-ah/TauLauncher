package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.With;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PackageIndex(String name, String uid, List<Version> versions) {
    public Optional<Version> version(String version) {
        return versions.stream().filter(v -> version.equals(v.version)).findFirst();
    }

    public record Version(
            boolean recommended,
            String releaseTime,
            @With List<Requirement> requires,
            String version,
            String sha256,
            @JsonAnySetter Map<String, Object> properties
    ) {
        public static Version simple(String version, List<Requirement> requires) {
            return new Version(false, null, requires, version, null, null);
        }
    }
}
