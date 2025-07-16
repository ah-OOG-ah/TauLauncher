package org.taumc.launcher.core.components;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.taumc.launcher.core.meta.prism.Component;
import org.taumc.launcher.core.meta.json.JsonDecoder;

public class BuiltinComponents {
    private static final ObjectMapper MAPPER = JsonDecoder.make();

    public static final String LEGACY_LAUNCH_WRAPPER_UID = "org.mcphackers.launchwrapper";
    public static final Component LEGACY_LAUNCH_WRAPPER = buildComponentFromJson("""
            {
                "+traits": [
                    "noapplet"
                ],
                "libraries": [
                    {
                        "name": "org.mcphackers:launchwrapper:1.1.2",
                        "url": "https://maven.glass-launcher.net/releases/"
                    },
                    {
                        "name": "org.ow2.asm:asm:9.7.1",
                        "url": "https://maven.fabricmc.net/"
                    },
                    {
                        "name": "org.ow2.asm:asm-tree:9.7.1",
                        "url": "https://maven.fabricmc.net/"
                    },
                    {
                        "name": "org.json:json:20240303",
                        "url": "https://repo1.maven.org/maven2/"
                    }
                ],
                "mainClass": "org.mcphackers.launchwrapper.Launch",
                "minecraftArguments": "--username ${auth_player_name} --session ${auth_session} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --accessToken ${auth_access_token} --userType ${user_type} --versionType ${version_type}",
                "requires": [
                    {
                        "uid": "net.minecraft"
                    }
                ],
                "formatVersion": 1,
                "name": "LaunchWrapper",
                "uid": "org.mcphackers.launchwrapper",
                "version": "1.1.2"
            }
    """);

    private static Component buildComponentFromJson(String json) {
        try {
            return MAPPER.readValue(json, Component.class);
        } catch (JsonProcessingException e) {
            throw new AssertionError("Failed to build component", e);
        }
    }
}
