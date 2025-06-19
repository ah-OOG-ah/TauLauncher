package org.taumc.launcher.core.launch;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.text.StringEscapeUtils;
import org.taumc.launcher.core.qsettings.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class InstanceCfg {
    public static void configureInstanceWithCfg(RuntimeInstance instance, Path path) throws IOException {
        Settings settings;
        try (var is = Files.newInputStream(path)) {
            settings = Settings.read(is);
        } catch (NoSuchFileException e) {
            return;
        }
        configureInstanceWithSettings(instance, settings);
    }

    public static Optional<Integer> tryParseInt(String val) {
        try {
            return Optional.of(Integer.parseInt(val));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Optional<Boolean> tryParseBool(String val) {
        if (val.equals("true")) {
            return Optional.of(Boolean.TRUE);
        } else if (val.equals("false")) {
            return Optional.of(Boolean.FALSE);
        } else {
            return Optional.empty();
        }
    }

    public static void configureInstanceWithSettings(RuntimeInstance instance, Settings settings) {
        settings.getNested("General").ifPresent(generalSettings -> {
            generalSettings.getValue("MinMemAlloc").flatMap(InstanceCfg::tryParseInt).ifPresent(instance::setMinimumMemoryMB);
            generalSettings.getValue("MaxMemAlloc").flatMap(InstanceCfg::tryParseInt).ifPresent(instance::setMaximumMemoryMB);
            generalSettings.getValue("OverrideJavaArgs").flatMap(InstanceCfg::tryParseBool).filter(Boolean::booleanValue).flatMap($ -> generalSettings.getValue("JvmArgs")).ifPresent(args -> {
                var line = new CommandLine("dummy");
                String finalArgs = args;
                if (args.length() >= 2 && args.startsWith("\"") && args.endsWith("\"")) {
                    finalArgs = StringEscapeUtils.unescapeJava(args.substring(1, args.length() - 1));
                }
                line.addArguments(finalArgs, false);
                instance.setExtraJvmArguments(List.of(line.getArguments()));
            });
        });
    }
}
