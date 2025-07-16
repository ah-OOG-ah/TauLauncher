package org.taumc.launcher.core.meta.component;

import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.Requirement;

import java.util.List;

public interface GameComponent extends ComponentCoordinate {
    default boolean doesProvide(String uid) {
        return uid().equals(uid);
    }

    default List<Requirement> requires() {
        return List.of();
    }

    default String name() {
        return uid();
    }
}
