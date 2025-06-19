package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MMCPack(int formatVersion, List<Component> components) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Component(String uid, String version) implements ComponentCoordinate {}

    public static MMCPack read(Path path) throws IOException {
        return new ObjectMapper().readValue(Files.newInputStream(path), MMCPack.class);
    }
}
