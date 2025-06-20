package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record Component(
        String name,
        String uid,
        String version,
        int order,
        String releaseTime,
        Optional<String> mainClass,
        List<Library> libraries,
        List<Requirement> requires,
        Optional<Library> mainJar,
        Optional<Artifact> assetIndex,
        List<Library> mavenFiles,
        List<String> provides,
        @JsonProperty("+traits") List<String> traits,
        @JsonAnySetter Map<String, Object> extraProperties
) implements ComponentCoordinate {
    public Object extraProperty(String name) {
        return extraProperties != null ? extraProperties.get(name) : null;
    }

    public boolean doesProvide(String uid) {
        if (uid.equals(this.uid)) {
            return true;
        }
        return provides != null && provides.contains(uid);
    }
}
