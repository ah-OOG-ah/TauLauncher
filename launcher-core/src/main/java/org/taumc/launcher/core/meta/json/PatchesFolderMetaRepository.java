package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record PatchesFolderMetaRepository(Path patchesFolder) implements MetaRepository {
    private static final ObjectMapper MAPPER = JsonDecoder.make();

    @Override
    public RootIndex getRootIndex() throws IOException {
        try(Stream<Path> stream = Files.list(patchesFolder)) {
            return new RootIndex(1, stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> !Files.isDirectory(p))
                    .map(p -> p.getFileName().toString())
                    .map(s -> s.substring(0, s.length() - 5))
                    .map(s -> new RootIndex.Package(s, s))
                    .toList());
        }
    }

    @Override
    public PackageIndex getPackageIndex(String pkgName) throws IOException {
        var component = getComponent(pkgName, null);
        return new PackageIndex(pkgName, pkgName, List.of(new PackageIndex.Version(
                false,
                component.releaseTime(),
                component.requires(),
                component.version(),
                "",
                Map.of()
        )), null);
    }

    @Override
    public Component getComponent(String pkgName, String version) throws IOException {
        var json = patchesFolder.resolve(pkgName + ".json");
        var component = MAPPER.readValue(Files.newInputStream(json), Component.class);
        if (version == null || component.version().equals(version)) {
            return component;
        } else {
            throw new IOException("Component in patches folder does not exist with desired version");
        }
    }

    @Override
    public void close() {

    }
}
