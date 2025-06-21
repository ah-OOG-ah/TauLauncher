package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Singular;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
public record Component(
        String name,
        String uid,
        String version,
        int order,
        String releaseTime,
        Optional<String> mainClass,
        List<Library> libraries,
        @Singular List<Requirement> requires,
        Optional<Library> mainJar,
        Optional<Artifact> assetIndex,
        List<Library> mavenFiles,
        List<String> provides,
        @Singular List<Integer> compatibleJavaMajors,
        @Singular @JsonProperty("+traits") List<String> traits,
        @Singular @JsonAnySetter Map<String, Object> extraProperties
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

    public static class ComponentBuilder {
        ComponentBuilder() {
            this.assetIndex = Optional.empty();
            this.mainJar = Optional.empty();
            this.mainClass = Optional.empty();
        }

        public boolean removeRequirementIf(Predicate<Requirement> predicate) {
            return this.requires.removeIf(predicate);
        }

        public ComponentBuilder packageIndex(PackageIndex packageIndex, PackageIndex.Version version) {
            requires(version.requires());
            version(version.version());
            name(packageIndex.name());
            uid(packageIndex.uid());
            return this;
        }
    }
}
