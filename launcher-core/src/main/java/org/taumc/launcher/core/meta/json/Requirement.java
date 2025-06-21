package org.taumc.launcher.core.meta.json;

import java.util.List;
import java.util.Optional;

public record Requirement(String uid, Optional<String> suggests, Optional<String> equals) {
    public boolean isSatisfied(List<? extends ComponentCoordinate> components, MetadataService metadataService) {
        boolean foundComponent = false;
        for (var component : components) {
            var fullComponent = metadataService.getComponent(component);
            if (fullComponent.doesProvide(uid)) {
                foundComponent |= equals.isEmpty() || component.version().equals(equals.get());
            }
        }
        return foundComponent;
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

    public static Requirement approximately(String uid, String version) {
        return new Requirement(uid, Optional.of(version), Optional.empty());
    }

    public static Requirement any(String uid) {
        return new Requirement(uid, Optional.empty(), Optional.empty());
    }
}
