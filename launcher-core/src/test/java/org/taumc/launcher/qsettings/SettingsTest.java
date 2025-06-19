package org.taumc.launcher.qsettings;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.taumc.launcher.core.qsettings.Settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SettingsTest {
    @Test
    void testRoundtrip() throws Exception {
        Path path = Paths.get(SettingsTest.class.getResource("/sample_instance.cfg").toURI());
        String original = Files.readString(path);
        Settings settings = Settings.read(Files.newInputStream(path));
        String serialized = settings.serializeToString();
        Assertions.assertEquals(original.strip(), serialized.strip());
    }
}
