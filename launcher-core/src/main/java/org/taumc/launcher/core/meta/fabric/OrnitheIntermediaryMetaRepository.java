package org.taumc.launcher.core.meta.fabric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.CacheControl;
import com.github.mizosoft.methanol.HttpCache;
import com.github.mizosoft.methanol.Methanol;
import com.vdurmont.semver4j.Semver;
import org.taumc.launcher.core.http.HttpUtils;
import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.GameComponent;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.prism.Component;
import org.taumc.launcher.core.meta.json.InMemoryMetaRepository;
import org.taumc.launcher.core.meta.json.JsonDecoder;
import org.taumc.launcher.core.meta.json.Library;
import org.taumc.launcher.core.meta.json.PackageIndex;
import org.taumc.launcher.core.meta.json.Requirement;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class OrnitheIntermediaryMetaRepository extends InMemoryMetaRepository {
    private static final String UID = "net.ornithemc.intermediary";
    private static final String NAME = "Ornithe Intermediary";
    private static final String META_URL = "https://meta.ornithemc.net";
    private static final HttpCache CACHE = HttpCache.newBuilder().cacheOnDisk(LauncherPaths.getLauncherCache().resolve("caches").resolve("fabric_meta"), 100 * 1024 * 1024).build();
    private static final Methanol CLIENT = Methanol.newBuilder()
            .cache(CACHE)
            .defaultHeader("Cache-Control", CacheControl.newBuilder().maxStale(Duration.ofMinutes(5)).staleIfError(Duration.ofSeconds(Long.MAX_VALUE)).build().toString())
            .build();
    private static final ObjectMapper MAPPER = JsonDecoder.make();

    public OrnitheIntermediaryMetaRepository() {
    }

    protected String getIntermediaryEndpoint() {
        return META_URL + "/v3/versions/gen1/game/intermediary";
    }

    protected String getIntermediaryDataEndpoint(String version) {
        return META_URL + "/v3/versions/gen1/intermediary/" + version;
    }

    private List<Requirement> buildRequirements(String version) {
        return List.of(new Requirement("net.minecraft", Optional.empty(), Optional.of(version)));
    }

    protected void populateRepository() throws IOException {
        List<GameVersion> intermediaryVersions = MAPPER.readValue(HttpUtils.obtainFile(CLIENT, getIntermediaryEndpoint()), new TypeReference<>() {});
        var packageIndex = new PackageIndex(NAME, UID,
                intermediaryVersions.stream().filter(gv -> !gv.version().contains("server")).map(gv -> PackageIndex.Version.simple(gv.version(), buildRequirements(gv.unsidedVersion())))
                        .toList(), null);
        this.addPackage(packageIndex);
    }
    
    private record MaxVersionLibrary(String maximumVersion, Library library) {}

    private static final String minecraftUrl = "https://libraries.minecraft.net/";
    private static final String ornitheMavenUrl = "https://maven.ornithemc.net/releases";

    // Keep in sync with https://github.com/OrnitheMC/ornithe-meta/blob/main/src/main/java/net/ornithemc/meta/web/ProfileLibraryManager.java
    private static final List<MaxVersionLibrary> VERSION_SPECIFIC_LIBRARIES = List.of(
            new MaxVersionLibrary(null, Library.fromMaven("org.slf4j:slf4j-api:2.0.1", minecraftUrl)),
            new MaxVersionLibrary(null, Library.fromMaven("org.slf4j:slf4j-api:2.0.1", minecraftUrl)),
            new MaxVersionLibrary(null, Library.fromMaven("org.apache.logging.log4j:log4j-slf4j2-impl:2.19.0", minecraftUrl)),
            new MaxVersionLibrary(null, Library.fromMaven("org.apache.logging.log4j:log4j-api:2.19.0", minecraftUrl)),
            new MaxVersionLibrary(null, Library.fromMaven("org.apache.logging.log4j:log4j-core:2.19.0", minecraftUrl)),
            new MaxVersionLibrary(null, Library.fromMaven("it.unimi.dsi:fastutil:8.5.9", minecraftUrl)),
            new MaxVersionLibrary(null, Library.fromMaven("com.google.code.gson:gson:2.10", minecraftUrl)),
            new MaxVersionLibrary("1.7.0-alpha.13.38.c", Library.fromMaven("net.ornithemc:logger-config:1.0.0", ornitheMavenUrl)),
            new MaxVersionLibrary("1.5.2", Library.fromMaven("com.google.guava:guava:14.0", minecraftUrl)),
            new MaxVersionLibrary("1.7.5", Library.fromMaven("commons-codec:commons-codec:1.9", minecraftUrl)),
            new MaxVersionLibrary("1.7.10", Library.fromMaven("org.apache.commons:commons-compress:1.8.1", minecraftUrl)),
            new MaxVersionLibrary("1.5.2", Library.fromMaven("commons-io:commons-io:2.4", minecraftUrl)),
            new MaxVersionLibrary("1.5.2", Library.fromMaven("org.apache.commons:commons-lang3:3.1", minecraftUrl)),
            new MaxVersionLibrary("1.7.10", Library.fromMaven("commons-logging:commons-logging:1.1.3", minecraftUrl)),
            new MaxVersionLibrary("1.7.9", Library.fromMaven("org.apache.httpcomponents:httpcore:4.3.2", minecraftUrl)),
            new MaxVersionLibrary("1.7.9", Library.fromMaven("org.apache.httpcomponents:httpclient:4.3.3", minecraftUrl))
    );

    private List<Library> computeLibraries(IntermediaryVersion version) throws IOException {
        List<Library> libraries = new ArrayList<>();

        libraries.add(Library.fromMaven(version.maven(), ornitheMavenUrl));

        Map<String, Object> versionInfo = MAPPER.readValue(HttpUtils.obtainFile(CLIENT, "https://skyrising.github.io/mc-versions/version/" + version.versionNoSide() + ".json"), new TypeReference<Map<String, Object>>() {});
        String normalized = (String)versionInfo.getOrDefault("normalizedVersion", version.version());
        var semver = new Semver(normalized);
        
        for (var maxVerLib : VERSION_SPECIFIC_LIBRARIES) {
            if (maxVerLib.maximumVersion == null || semver.isLowerThanOrEqualTo(maxVerLib.maximumVersion)) {
                libraries.add(maxVerLib.library);
            }
        }

        return libraries;
    }

    @Override
    public CompletableFuture<ReconcilableGameComponent> retrieveComponent(String pkgName, String version) {
        try {
            if (pkgName.equals(UID)) {
                List<IntermediaryVersion> list = MAPPER.readValue(HttpUtils.obtainFile(CLIENT, getIntermediaryDataEndpoint(version)), new TypeReference<>() {});
                if (list.isEmpty()) {
                    throw new IOException("Intermediary data not found for " + version);
                }
                var intermediaryData = list.getFirst();
                return CompletableFuture.completedFuture(Component.builder()
                        .name(NAME)
                        .uid(UID)
                        .version(version)
                        .libraries(computeLibraries(intermediaryData))
                        .order(11)
                        .requires(buildRequirements(intermediaryData.versionNoSide()))
                        .provides(List.of("net.fabricmc.intermediary"))
                        .traits(List.of("noapplet"))
                        .build());
            } else {
                throw new IOException("Unknown component " + pkgName);
            }
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void close() {

    }
}
