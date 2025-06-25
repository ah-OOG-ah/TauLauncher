package org.taumc.launcher.core.meta.legacyforge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModInfo(String modid, String name, String description, String version, String logoFile) {
}
