package org.taumc.launcher.core.mods.curseforge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PackManifest(Minecraft minecraft, String manifestType, int manifestVersion, String name, String version, String author, List<File> files, String overrides) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Minecraft(String version, List<ModLoader> modLoaders) {}
    public record ModLoader(String id, boolean primary) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record File(int projectID, int fileID, boolean required, String downloadUrl) {}
}
