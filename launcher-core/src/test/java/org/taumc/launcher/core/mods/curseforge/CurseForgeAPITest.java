package org.taumc.launcher.core.mods.curseforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.taumc.launcher.core.mods.ModSearchOptions;
import org.taumc.launcher.core.mods.ProjectType;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

public class CurseForgeAPITest {
    private static CurseForgeAPI api;

    @BeforeAll
    static void setupApi() {
        api = new CurseForgeAPI();
    }

    @AfterAll
    static void teardownApi() {
        api.close();
    }

    @Test
    void testSearch() {
        var searchOptions = new ModSearchOptions();
        searchOptions.filterText = "modernfix";
        var firstMod = api.searchForMods(searchOptions).join().getFirst();
        Assertions.assertEquals(790626, firstMod.id());
    }

    @Test
    void testGet() {
        var firstMod = api.getMod(790626).join();
        Assertions.assertEquals("ModernFix", firstMod.name());
    }

    @Test
    void testGetFile() throws URISyntaxException {
        var firstFile = api.getModFile(790626, 6609557).join();
        Assertions.assertEquals("modernfix-neoforge-5.23.1+mc1.21.1.jar", firstFile.fileName());
        // Check that the download URL is valid
        new URI(firstFile.downloadUrl());
    }

    @Test
    void testGetFingerprints() {
        int hash = -905966600;
        var fingerprints = api.getFingerprintMatches(List.of(hash)).join();
        Assertions.assertEquals("modernfix-neoforge-5.23.1+mc1.21.1.jar", fingerprints.exactMatches().getFirst().file().fileName());
    }

    @Test
    void testGetModpack(@TempDir Path tempDir) throws Exception {
        var modpackFile = api.getModFile(1237007, 6641081).join();
        Path path = tempDir.resolve("modpack.zip");
        try (var client = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            var response = client.send(HttpRequest.newBuilder().GET().uri(new URI(modpackFile.downloadUrl())).build(), HttpResponse.BodyHandlers.ofFile(path));
            Assertions.assertEquals(200, response.statusCode());
        }
        PackManifest manifest;
        try (ZipFile zf = new ZipFile(path.toFile())) {
            var entry = zf.getEntry("manifest.json");
            Assertions.assertNotNull(entry, "manifest.json not in modpack");
            try (var is = zf.getInputStream(entry)) {
                manifest = new ObjectMapper().readValue(is, PackManifest.class);
            }
        }
        Assertions.assertEquals("Just Create SMP v0.3", manifest.name());
        var resolvedFiles = api.getFilesBulk(manifest.files().stream().map(PackManifest.File::fileID).toList()).join();
        var create = resolvedFiles.stream().filter(f -> f.modId() == 328085).findFirst().orElseThrow();
        Assertions.assertEquals("create-1.21.1-6.0.5.jar", create.fileName());
    }

    @Test
    void testModpackSearch() throws Exception {
        var searchOptions = new ModSearchOptions();
        searchOptions.filterText = "craftoria";
        searchOptions.projectType = ProjectType.MODPACK;
        var firstMod = api.searchForMods(searchOptions).join().getFirst();
        Assertions.assertEquals(1039252, firstMod.id());
    }
}
