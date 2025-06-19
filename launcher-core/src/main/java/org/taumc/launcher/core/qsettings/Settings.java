package org.taumc.launcher.core.qsettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class Settings implements Cloneable {
    private final Map<String, Settings> subSettings = new LinkedHashMap<>();
    private final Map<String, String> values = new LinkedHashMap<>();

    public void clear() {
        this.subSettings.clear();
        this.values.clear();
    }

    public Optional<Settings> getNested(String key) {
        return Optional.ofNullable(this.subSettings.get(key));
    }

    public Settings getOrCreateNested(String key) {
        return this.subSettings.computeIfAbsent(key, $ -> new Settings());
    }

    public Optional<String> getValue(String key) {
        return Optional.ofNullable(this.values.get(key));
    }

    public void setValue(String key, String value) {
        this.values.put(key, value);
    }

    public String serializeToString() {
        StringBuilder sb = new StringBuilder();
        serializeToString(sb);
        return sb.toString();
    }

    private void serializeToString(StringBuilder sb) {
        serializeValues(sb);
        var iter = subSettings.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            sb.append('[').append(entry.getKey()).append(']').append('\n');
            entry.getValue().serializeToString(sb);
            if (iter.hasNext()) {
                sb.append('\n');
            }
        }
    }

    private void serializeValues(StringBuilder sb) {
        for (var entry : values.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
    }

    @Override
    public Settings clone() {
        var newSettings = new Settings();
        newSettings.values.putAll(this.values);
        for (var entry : this.subSettings.entrySet()) {
            newSettings.subSettings.put(entry.getKey(), entry.getValue().clone());
        }
        return newSettings;
    }

    public void merge(Settings other) {
        this.values.putAll(other.values);
        for (var entry : other.subSettings.entrySet()) {
            var ourSubsetting = this.subSettings.get(entry.getKey());
            if (ourSubsetting != null) {
                ourSubsetting.merge(entry.getValue());
            } else {
                this.subSettings.put(entry.getKey(), entry.getValue().clone());
            }
        }
    }

    public void readFrom(Path path) throws IOException {
        try (var is = Files.newInputStream(path)) {
            readFrom(is);
        }
    }

    public void readFrom(InputStream stream) throws IOException {
        var settings = Settings.read(stream);
        this.merge(settings);
    }

    public void writeTo(Path path) throws IOException {
        Files.writeString(path, this.serializeToString());
    }

    public static Settings read(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            var processor = new SettingsReader(reader, new Settings());
            processor.process();
            return processor.settings;
        }
    }

    static class SettingsReader {
        private static final Pattern SECTION_PATTERN = Pattern.compile("^\\[([a-zA-Z]*)\\]$");
        private static final Logger LOGGER = LoggerFactory.getLogger(SettingsReader.class);

        private final BufferedReader reader;
        private final Settings settings;

        private Settings currentSection;

        SettingsReader(BufferedReader reader, Settings settings) {
            this.reader = reader;
            this.settings = settings;
            this.currentSection = settings;
        }

        private Settings findSection(String sectionName) {
            String[] components = sectionName.split("\\.");
            Settings current = settings;
            for (String c : components) {
                current = current.getOrCreateNested(c);
            }
            return current;
        }

        private void processLine(String line) {
            if (line.isBlank()) {
                return;
            }

            if (line.startsWith("[")) {
                var matcher = SECTION_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String sectionName = matcher.group(1);
                    currentSection = findSection(sectionName);
                } else {
                    LOGGER.warn("Ignoring unknown construct: {}", line);
                }
                return;
            }

            int eqIdx = line.indexOf('=');

            if (eqIdx == -1) {
                LOGGER.warn("Config option specified without value: {}", line);
                return;
            }

            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1);
            currentSection.values.put(key, value);
        }

        void process() throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line);
            }
        }
    }
}
