package org.taumc.launcher.core.jvm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JavaRuntime(String name, String downloadType, String packageType, String runtimeOS, String url, Version version) {
    public record Version(int build, int major, int minor, int security) {}
}
