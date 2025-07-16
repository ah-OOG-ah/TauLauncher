package org.taumc.launcher.meta.json;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.taumc.launcher.core.meta.prism.Component;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;
import org.taumc.launcher.core.meta.json.MetadataService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetadataServiceTest {
    private static final MetadataService SERVICE = new MetadataService();

    @BeforeAll
    static void setupService() throws Exception {
        SERVICE.addRepository(HTTPMetaRepository.prism());
        SERVICE.updateIndex();
    }


    @Test
    void findsMinecraftPackage() throws Exception {
        assertTrue(SERVICE.getKnownPackages().contains("net.minecraft"));
    }

    @Test
    void findsMinecraftVersion() throws Exception {
        assertTrue(SERVICE.getKnownVersions("net.minecraft").join().stream().anyMatch(g -> g.version().equals("1.21.5")));
        var component = (Component)SERVICE.getComponent("net.minecraft", "1.21.5").join();
        assertEquals("release", component.extraProperty("type"));
    }

    @AfterAll
    static void closeService() {
        SERVICE.close();
    }
}
