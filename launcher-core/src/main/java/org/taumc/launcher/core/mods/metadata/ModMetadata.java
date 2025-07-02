package org.taumc.launcher.core.mods.metadata;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.meta.legacyforge.ModInfo;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Represents mod metadata for a given mod jar.
 */
public record ModMetadata(String logoPath, String name, String version) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModMetadata.class);

    private static ModMetadata computeForgeMetadata(ZipEntry modsToml, ZipFile file) throws IOException {
        TomlParseResult toml;
        try (var is = file.getInputStream(modsToml)) {
            toml = Toml.parse(is);
        }
        var mods = toml.getArrayOrEmpty("mods");
        String logoFile = null;
        String name = null, version = null;
        if (!mods.isEmpty()) {
            var modData = mods.getTable(0);
            logoFile = Objects.requireNonNullElse(modData.get("logoFile"), "").toString();
            name = Objects.requireNonNullElse(modData.get("displayName"), "").toString();
            if (name.isBlank()) {
                name = null;
            }
            version = Objects.requireNonNullElse(modData.get("version"), "").toString();
            if (version.equals("${file.jarVersion}") && file.getEntry("META-INF/MANIFEST.MF") instanceof ZipEntry me) {
                try (var is = file.getInputStream(me)) {
                    Manifest manifest = new Manifest(is);
                    version = Objects.requireNonNullElse(manifest.getMainAttributes().getValue("Implementation-Version"), "");
                }
            }
        }
        return new ModMetadata(logoFile, name, version);
    }

    private static ModMetadata computeLegacyForgeMetadata(ZipEntry mcmodInfo, ZipFile file) throws IOException {
        try (var is = file.getInputStream(mcmodInfo)) {
            var mapper = JsonMapper.builder()
                    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, true)
                    .build();
            var node = mapper.readTree(is);
            List<ModInfo> modInfos;
            if (node.isArray()) {
                modInfos = mapper.treeToValue(node, new TypeReference<>() {});
            } else if (node.isObject()) {
                modInfos = mapper.treeToValue(node.get("modList"), new TypeReference<>() {});
            } else {
                return null;
            }
            if (modInfos.isEmpty()) {
                return null;
            }
            var modInfo = modInfos.getFirst();
            return new ModMetadata(modInfo.logoFile(), modInfo.name(), modInfo.version());
        }
    }
    public static CompletableFuture<ModMetadata> computeFor(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try (ZipFile zf = new ZipFile(path.toFile())) {
                var neoforgeMod = zf.getEntry("META-INF/neoforge.mods.toml");
                if (neoforgeMod != null) {
                    return computeForgeMetadata(neoforgeMod, zf);
                }
                var forgeMod = zf.getEntry("META-INF/mods.toml");
                if (forgeMod != null) {
                    return computeForgeMetadata(forgeMod, zf);
                }
                var legacyForgeMod = zf.getEntry("mcmod.info");
                if (legacyForgeMod != null) {
                    return computeLegacyForgeMetadata(legacyForgeMod, zf);
                }
            } catch (Exception e) {
                LOGGER.error("Error computing mod metadata for {}", path.getFileName().toString(), e);
            }
            return null;
        });
    }
}
