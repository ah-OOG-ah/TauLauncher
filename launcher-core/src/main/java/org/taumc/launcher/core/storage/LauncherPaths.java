package org.taumc.launcher.core.storage;

import java.nio.file.Path;
import java.nio.file.Paths;

public class LauncherPaths {
    public static Path getLauncherCache() {
        if (Boolean.getBoolean("tau.launcher.portable")) {
            return Paths.get("taulauncher");
        } else {
            return getUserDataDir("TauLauncher");
        }
    }

    public static Path getUserDataDir(String appName) {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            // Windows: use %APPDATA% or %LOCALAPPDATA%
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                return Paths.get(appData, appName);
            } else {
                // fallback to user home
                return Paths.get(userHome, "AppData", "Roaming", appName);
            }
        } else if (os.contains("mac")) {
            // macOS: ~/Library/Application Support/
            return Paths.get(userHome, "Library", "Application Support", appName);
        } else {
            // Linux/Unix: follow XDG spec or fallback to ~/.local/share
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            if (xdgDataHome != null && !xdgDataHome.isEmpty()) {
                return Paths.get(xdgDataHome, appName);
            } else {
                return Paths.get(userHome, ".local", "share", appName);
            }
        }
    }
}
