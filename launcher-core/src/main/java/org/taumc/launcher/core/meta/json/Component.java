package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Singular;
import org.taumc.launcher.core.components.BuiltinComponents;
import org.taumc.launcher.core.launch.RuntimeInstance;
import org.taumc.launcher.core.meta.component.GameComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
        @Singular @JsonProperty("+agents") List<Library> agents,
        @Singular @JsonProperty("+jvmArgs") List<String> jvmArgs,
        @Singular @JsonProperty("+tweakers") List<String> tweakers,
        String minecraftArguments,
        @Singular @JsonAnySetter Map<String, Object> extraProperties
) implements GameComponent {
    public Object extraProperty(String name) {
        return extraProperties != null ? extraProperties.get(name) : null;
    }

    public boolean doesProvide(String uid) {
        if (uid.equals(this.uid)) {
            return true;
        }
        return provides != null && provides.contains(uid);
    }

    @Override
    public List<Requirement> requires() {
        var traits = this.traits != null ? this.traits : List.of();

        var requires = new ArrayList<Requirement>();

        if (this.requires != null) {
            requires.addAll(this.requires);
        }

        if ((traits.contains("legacyLaunch") || traits.contains("alphaLaunch")) && !traits.contains("noapplet")) {
            requires.add(new Requirement(BuiltinComponents.LEGACY_LAUNCH_WRAPPER_UID, Optional.empty(), Optional.empty()));
        }

        return requires;
    }

    @Override
    public CompletableFuture<Void> reconcile(RuntimeInstance instance, Executor configurationExecutor) {
        return CompletableFuture.runAsync(() -> {
            if (this.mavenFiles != null) {
                instance.addMavenDownloads(this.mavenFiles);
            }
            if (this.libraries != null) {
                instance.addLibraries(this.libraries);
            }
            this.mainJar.ifPresent(library -> instance.addLibraries(List.of(library)));
            this.mainClass.ifPresent(instance::setMainClassName);
            if (this.jvmArgs != null) {
                instance.addExtraJvmArguments(this.jvmArgs);
            }
            if (this.compatibleJavaMajors != null) {
                this.compatibleJavaMajors.stream().mapToInt(Integer::intValue).max().ifPresent(instance::setJavaVersion);
            }
            if (this.minecraftArguments != null) {
                for (var arg : this.minecraftArguments.split(" ")) {
                    instance.addGameArgument(arg);
                }
            }
            if (this.tweakers != null) {
                for (String tweakClass : this.tweakers) {
                    instance.addGameArgument("--tweakClass");
                    instance.addGameArgument(tweakClass);
                }
            }
            if (this.agents != null) {
                this.agents.forEach(instance::addJavaAgent);
            }
            if (this.uid.equals("net.minecraft")) {
                var params = instance.getGameArgumentTemplateParameters();
                params.put("version_name", this.version());
                params.put("version_type", (String)this.extraProperties().get("type"));
            }
        }, configurationExecutor);
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
