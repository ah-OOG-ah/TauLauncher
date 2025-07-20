package org.taumc.launcher.core.meta.component;

import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.Requirement;

import java.util.List;
import java.util.Set;

public interface GameComponent extends ComponentCoordinate {
    default Set<String> providedUids() {
        return Set.of(uid());
    }

    default List<Requirement> requires() {
        return List.of();
    }

    default String name() {
        return uid();
    }

    default String friendlyVersion() {
        return version();
    }
}
