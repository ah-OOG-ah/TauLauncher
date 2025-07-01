package org.taumc.launcher.core.mods.modpacksch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModpackVersion(List<File> files, List<Target> targets, String name, String type, int parent, int id) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record File(String path, String url, boolean clientonly, boolean serveronly, boolean optional, long size, int id, String name, CurseForgeListing curseforge) {}

    public record CurseForgeListing(int project, int file) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Target(String type, String name, String version) {}
}
