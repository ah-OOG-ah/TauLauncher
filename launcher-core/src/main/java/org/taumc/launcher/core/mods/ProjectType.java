package org.taumc.launcher.core.mods;

import java.util.Collection;
import java.util.Set;

public enum ProjectType {
    MOD,
    MODPACK;

    public static final Collection<ProjectType> ALL_TYPES = Set.of(ProjectType.values());
}
