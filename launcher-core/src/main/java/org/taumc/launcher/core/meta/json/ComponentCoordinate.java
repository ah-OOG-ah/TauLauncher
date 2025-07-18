package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public interface ComponentCoordinate {
    String uid();
    String version();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Simple(String uid, String version) implements ComponentCoordinate {
        @Override
        public String toString() {
            return uid + "@" + version;
        }
    }
}
