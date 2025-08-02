package org.taumc.launcher.core.meta.prism;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.JsonDecoder;
import org.taumc.launcher.core.meta.json.MetaRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record PatchesFolderMetaRepository(Path patchesFolder) implements MetaRepository {
    private static final ObjectMapper MAPPER = JsonDecoder.make();

    @Override
    public CompletableFuture<Set<String>> getKnownPackages() {
        try (Stream<Path> stream = Files.list(patchesFolder)) {
            return CompletableFuture.completedFuture( stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> !Files.isDirectory(p))
                    .map(p -> p.getFileName().toString())
                    .map(s -> s.substring(0, s.length() - 5))
                    .collect(Collectors.toUnmodifiableSet()));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<ReconcilableGameComponent> retrieveComponent(String pkgName, String version) {
        var json = patchesFolder.resolve(pkgName + ".json");
        Component component;
        try {
            component = MAPPER.readValue(Files.newInputStream(json), Component.class);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        if (version == null || component.version().equals(version)) {
            return CompletableFuture.completedFuture(component);
        } else {
            return CompletableFuture.failedFuture(new IOException("Component in patches folder does not exist with desired version"));
        }
    }

    @Override
    public CompletableFuture<ComponentMetaInfo> retrieveComponentMeta(String pkgName) {
        if (Files.exists(patchesFolder.resolve(pkgName + ".json"))) {
            return CompletableFuture.completedFuture(new ComponentMetaInfo() {
                @Override
                public String name() {
                    return pkgName;
                }

                @Override
                public boolean isUserInstallable() {
                    return false;
                }
            });
        } else {
            return CompletableFuture.failedFuture(new IOException("Does not exist"));
        }
    }

    @Override
    public void close() {

    }
}
