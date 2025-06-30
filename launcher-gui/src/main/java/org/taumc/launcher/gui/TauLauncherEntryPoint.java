package org.taumc.launcher.gui;

import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public class TauLauncherEntryPoint {
    public static void main(String[] args) throws Exception {
        System.setProperty("taulauncher.logdir", LauncherPaths.getLauncherCache().resolve("logs").toAbsolutePath().toString());
        String version = TauLauncherEntryPoint.class.getPackage().getImplementationVersion();
        if (version == null) {
            try (InputStream in = TauLauncherEntryPoint.class.getResourceAsStream("/taulauncher_version.properties")) {
                Properties props = new Properties();
                if (in != null) {
                    props.load(in);
                    version = props.getProperty("version");
                }
            } catch (Exception ignored) {
                version = "UNKNOWN";
            }
        }
        System.setProperty("taulauncher.version", Objects.requireNonNullElse(version, "DEV"));
        Main.start(args);
    }
}
