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

    public String recommendedVersion() {
        if (equals.isPresent()) {
            return equals.get();
        }
        if (suggests.isPresent()) {
            return suggests.get();
        }
        throw new IllegalStateException("Don't know how to recommend a version for component " + uid);
    }

    @Override
    public String toString() {
        return uid + " = " + (equals.isEmpty() ? "~"  : "") + recommendedVersion();
    }
}
