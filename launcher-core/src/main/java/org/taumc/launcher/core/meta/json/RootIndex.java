package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public record RootIndex(int formatVersion, List<Package> packages) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Package(String name, String uid) {}
}
