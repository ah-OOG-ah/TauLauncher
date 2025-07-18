package org.taumc.launcher.core.meta.json;

import java.util.Map;
import java.util.Optional;

public record Requirement(String uid, Optional<String> suggests, Optional<String> equals) {
    public boolean isSatisfied(Map<String, ? extends ComponentCoordinate> components, MetadataService metadataService) {
        var coord = components.get(uid);
        if (coord != null) {
            return equals.isEmpty() || coord.version().equals(equals.get());
        } else {
            return false;
        }
    }

    public Optional<String> recommendedVersion() {
        if (equals.isPresent()) {
            return equals;
        }
        return suggests;
    }

    @Override
    public String toString() {
        return uid + " = " + (equals.isEmpty() ? "~"  : "") + recommendedVersion();
    }

    public static Requirement strict(String uid, String version) {
        return new Requirement(uid, Optional.empty(), Optional.of(version));
    }

    public static Requirement strict(ComponentCoordinate coordinate) {
        return strict(coordinate.uid(), coordinate.version());
    }

    public static Requirement approximately(String uid, String version) {
        return new Requirement(uid, Optional.of(version), Optional.empty());
    }

    public static Requirement any(String uid) {
        return new Requirement(uid, Optional.empty(), Optional.empty());
    }
}
