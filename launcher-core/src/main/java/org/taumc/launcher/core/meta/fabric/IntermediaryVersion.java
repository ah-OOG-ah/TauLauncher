package org.taumc.launcher.core.meta.fabric;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IntermediaryVersion(String version, boolean stable, String maven, String versionNoSide) {
}
