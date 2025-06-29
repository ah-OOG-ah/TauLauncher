package org.taumc.launcher.gui;

import org.taumc.launcher.core.storage.LauncherPaths;

public class TauLauncherEntryPoint {
    public static void main(String[] args) throws Exception {
        System.setProperty("taulauncher.logdir", LauncherPaths.getLauncherCache().resolve("logs").toAbsolutePath().toString());
        Main.start(args);
    }
}
