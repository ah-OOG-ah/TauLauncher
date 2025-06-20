package org.taumc.launcher.core.meta.fabric;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameVersion(String version, boolean stable, String versionNoSide) {
    public String unsidedVersion() {
        return versionNoSide != null ? versionNoSide : version;
    }
}
