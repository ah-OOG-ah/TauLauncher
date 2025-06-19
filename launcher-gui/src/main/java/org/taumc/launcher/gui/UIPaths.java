package org.taumc.launcher.gui;

import org.taumc.launcher.core.storage.LauncherPaths;

import java.nio.file.Path;

public class UIPaths {
    public static final Path INSTANCES_FOLDER = LauncherPaths.getLauncherCache().resolve("instances");
}
