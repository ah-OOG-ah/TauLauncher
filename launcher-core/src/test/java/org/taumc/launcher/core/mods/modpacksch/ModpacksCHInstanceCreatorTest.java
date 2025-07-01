package org.taumc.launcher.core.mods.modpacksch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.taumc.launcher.core.meta.json.JsonDecoder;
import org.taumc.launcher.core.mods.curseforge.CurseForgeInstanceCreator;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModpacksCHInstanceCreatorTest {
    @Test
    void testInstanceCreation(@TempDir Path tempDir) throws Exception {
        ModpackVersion version = JsonDecoder.make().readValue(ModpacksCHInstanceCreator.class.getResource("/modpacksch_modpack.json"), ModpackVersion.class);
        var creator = new ModpacksCHInstanceCreator(new CurseForgeInstanceCreator.ManualDownloadService() {
            @Override
            public void trackFileForManualDownload(Download download) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<Void> downloadManualFiles() {
                return CompletableFuture.completedFuture(null);
            }
        });
        Path instancePath = tempDir.resolve("instance");
        Files.createDirectories(instancePath);
        creator.createInstance(instancePath, version, ProgressProvider.NONE);
        assertTrue(Files.exists(instancePath.resolve("minecraft").resolve("mods").resolve("Placebo-1.21-9.3.4.jar")));
        assertTrue(Files.exists(instancePath.resolve("minecraft").resolve("config").resolve("Mekanism").resolve("additions-client.toml")));
    }
}
