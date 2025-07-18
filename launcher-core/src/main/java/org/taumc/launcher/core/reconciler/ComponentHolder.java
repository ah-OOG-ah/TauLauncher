package org.taumc.launcher.core.reconciler;

import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.MetadataService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComponentHolder {
    private final MetadataService service;
    private final Map<String, ReconcilableGameComponent> components = new HashMap<>();

    public ComponentHolder(MetadataService service) {
        this.service = service;
    }

    public ComponentHolder(ComponentHolder other) {
        this(other.service);
        this.components.putAll(other.components);
    }

    public void addComponent(String uid, String version) {
        addComponent(new ComponentCoordinate.Simple(uid, version));
    }

    public void addComponent(ComponentCoordinate.Simple coordinate) {
        var component = this.service.getComponent(coordinate.uid(), coordinate.version());
        try {
            addComponent(component.join());
        } catch (Exception e) {
            throw new RuntimeException("Exception locating component " + coordinate, e);
        }
    }

    public void addComponent(ReconcilableGameComponent component) {
        component.providedUids().forEach(uid -> this.components.put(uid, component));
    }

    public void addComponents(List<ComponentCoordinate.Simple> components) {
        for (var component : components) {
            this.addComponent(component);
        }
    }

    public Map<String, ReconcilableGameComponent> getComponents() {
        return Collections.unmodifiableMap(components);
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }
}
