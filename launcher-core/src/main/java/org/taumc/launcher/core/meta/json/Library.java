package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.SystemUtils;
import org.taumc.launcher.core.jvm.JavaService;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

public record Library(String name, Optional<String> url, Optional<Download> downloads, ExtractConfig extract, Map<String, String> natives, List<Rule> rules, @JsonProperty("MMC-hint") String mmcHint) {
    public static final String NATIVE_CATEGORY = findNativeCategory();

    private static String findNativeCategory() {
        if (SystemUtils.IS_OS_LINUX) {
            return "linux";
        } else if (SystemUtils.IS_OS_MAC) {
            return "osx";
        } else if (SystemUtils.IS_OS_WINDOWS) {
            return "windows";
        } else {
            throw new IllegalStateException("Unknown OS");
        }
    }

    public record ExtractConfig(List<String> exclude) {}
    public record Download(Artifact artifact, Map<String, Artifact> classifiers) {}

    public record Rule(Action action, @JsonAnySetter Map<String, Object> filters) {
        public enum Action {
            ALLOW(true),
            DISALLOW(false);

            private final boolean expectedMatchState;

            Action(boolean expectedMatchState) {
                this.expectedMatchState = expectedMatchState;
            }
        };

        private static final Map<String, Predicate<Object>> filterHandlers = new HashMap<>();

        static {
            filterHandlers.put("os", o -> {
                var name = ((Map<String, String>)o).get("name");
                return name.equals(JavaService.EXPECTED_JVM_OS) || name.equals(NATIVE_CATEGORY);
            });
        }

        public boolean passes() {
            for (var filter : Objects.requireNonNullElse(filters, Map.<String, Object>of()).entrySet()) {
                var handler = filterHandlers.get(filter.getKey());
                if (handler != null) {
                    var matches = handler.test(filter.getValue());
                    if (matches != action.expectedMatchState) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private String buildUrl() {
        String rawUrl = url.orElse("https://libraries.minecraft.net") + "/" + diskPath();
        try {
            return new URI(rawUrl).normalize().toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URI is invalid: " + rawUrl, e);
        }
    }

    public record ExplodedName(String group, String name, String version, String classifier, String extension) {
        public String diskPath() {
            return "%s/%s/%s/%s".formatted(
                    this.group().replace('.', '/'),
                    this.name(),
                    this.version(),
                    this.fileName()
            );
        }

        public String fileName() {
            return "%s-%s%s.%s".formatted(
                    this.name(),
                    this.version(),
                    !this.classifier().isEmpty() ? ("-" + this.classifier) : "",
                    this.extension
            );
        }

        public ExplodedName withClassifier(String classifier) {
            return new ExplodedName(group, name, version, classifier, extension);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(group).append(':').append(name).append(':').append(version);
            if (!classifier.isEmpty()) {
                sb.append(':').append(classifier);
            }
            if (!extension.equals("jar")) {
                sb.append('@').append(extension);
            }
            return sb.toString();
        }
    }

    public ExplodedName explodedName() {
        return explodeName(this.name);
    }

    public static ExplodedName explodeName(String name) {
        int extensionIdx = name.indexOf('@');
        String extension = "jar";
        if (extensionIdx != -1) {
            extension = name.substring(extensionIdx + 1);
            name = name.substring(0, extensionIdx);
        }
        var nameComponents = name.split(":");
        return new ExplodedName(
                nameComponents[0],
                nameComponents[1],
                nameComponents[2],
                nameComponents.length >= 4 ? nameComponents[3] : "",
                extension
        );
    }

    public String diskPath() {
        return explodedName().diskPath();
    }

    public Download findDownload() {
        return downloads.orElseGet(() -> new Download(new Artifact(Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), buildUrl(), Map.of()), Map.of()));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Library library = (Library) o;
        return Objects.equals(name, library.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    public static Library fromMaven(String name, String url) {
        return new Library(name, Optional.of(url), Optional.empty(), null, null, null, null);
    }
}
