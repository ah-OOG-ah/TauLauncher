package org.taumc.launcher.core.reconciler.intervention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ManualDownloadIntervention {
    private static final Path DOWNLOADS = getDownloadsFolder();
    private static final Logger LOGGER = LoggerFactory.getLogger(ManualDownloadIntervention.class);

    private static Path getDownloadsFolder() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();

        // This covers most common OSes
        if (os.contains("win")) {
            return Paths.get(userHome, "Downloads");
        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Downloads");
        } else if (os.contains("nux") || os.contains("nix")) {
            return Paths.get(userHome, "Downloads");
        }

        // Fallback
        return Paths.get(userHome);
    }

    public static InterventionAction forUrl(String url, String expectedFileName, long expectedFileSize, Path targetPath) {
        var message = "You must download " + expectedFileName + " manually from the following URL: " + url;
        return new InterventionAction(message, () -> {}, () -> {
            Path file = DOWNLOADS.resolve(expectedFileName);
            try {
                if (Files.size(file) != expectedFileSize) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
            LOGGER.info("Detected download {}", file);
            try {
                Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        });
    }
}
