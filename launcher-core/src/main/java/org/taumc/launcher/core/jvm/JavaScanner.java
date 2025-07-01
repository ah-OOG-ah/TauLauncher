package org.taumc.launcher.core.jvm;

import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaScanner {
    private static final String JAVA_EXEC_NAME = SystemUtils.IS_OS_WINDOWS ? "javaw.exe" : "java";

    public static String detectJavaVersion(Path javaExecutable) {
        try {
            Process process = new ProcessBuilder(javaExecutable.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("version")) {
                        int start = line.indexOf('"');
                        int end = line.lastIndexOf('"');
                        if (start >= 0 && end > start) {
                            return "Java " + line.substring(start + 1, end);
                        }
                    }
                }
            }

            process.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Java unknown";
    }

    public static List<JvmInstallation> discoverInstalledJvms() {
        List<JvmInstallation> found = new ArrayList<>();

        // 1. Check JAVA_HOME environment variable
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            Path javaPath = Paths.get(javaHome, "bin", JAVA_EXEC_NAME);
            if (Files.isExecutable(javaPath)) {
                found.add(new JvmInstallation("JAVA_HOME", javaPath));
            }
        }

        // 2. Check java from PATH environment variable by running "which" or "where"
        Path javaInPath = findJavaInPath();
        if (javaInPath != null) {
            found.add(new JvmInstallation(detectJavaVersion(javaInPath) + " (from PATH)", javaInPath));
        }

        // 3. Scan common install locations per OS
        List<Path> commonRoots = commonJavaInstallDirs();
        for (Path root : commonRoots) {
            if (Files.isDirectory(root)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                    for (Path subdir : stream) {
                        Path javaExe = subdir.resolve("bin").resolve(JAVA_EXEC_NAME);
                        if (Files.isExecutable(javaExe)) {
                            String displayName = detectJavaVersion(javaExe);
                            found.add(new JvmInstallation(displayName, javaExe));
                        }
                    }
                } catch (Exception e) {
                    // Ignore errors, e.g., permissions
                }
            }
        }

        // Map canonical path -> list of originals pointing there
        Map<Path, List<JvmInstallation>> canonicalToOriginals = found.stream()
                .collect(Collectors.groupingBy(p -> {
                    try {
                        return p.javaExecutable().toRealPath();
                    } catch (IOException e) {
                        throw new RuntimeException(e); // wrap checked in unchecked for streams
                    }
                }, LinkedHashMap::new, Collectors.toList()));

        // For each canonical path group, apply the filter logic
        return canonicalToOriginals.entrySet().stream()
                .flatMap(entry -> {
                    Path canonical = entry.getKey();
                    List<JvmInstallation> originals = entry.getValue();
                    var canonicalInstall = originals.stream().filter(o -> o.javaExecutable.equals(canonical)).findFirst();
                    return canonicalInstall.map(Stream::of).orElseGet(originals::stream);
                }).sorted(Comparator.comparing(c -> c.displayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static Path findJavaInPath() {
        String command = SystemUtils.IS_OS_WINDOWS ? "where" : "which";
        try {
            Process process = new ProcessBuilder(command, JAVA_EXEC_NAME).start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    Path path = Paths.get(line.trim());
                    if (Files.isExecutable(path)) {
                        return path;
                    }
                }
            }
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore errors
        }
        return null;
    }

    private static List<Path> commonJavaInstallDirs() {
        List<Path> roots = new ArrayList<>();
        String userHome = System.getProperty("user.home");

        if (SystemUtils.IS_OS_WINDOWS) {
            roots.add(Paths.get("C:", "Program Files", "Java"));
            roots.add(Paths.get("C:", "Program Files (x86)", "Java"));
            // Also check user AppData for SDKMAN or other installs if you want
        } else if (SystemUtils.IS_OS_MAC) {
            roots.add(Paths.get("/Library", "Java", "JavaVirtualMachines"));
            roots.add(Paths.get(userHome, "Library", "Java", "JavaVirtualMachines"));
        } else { // Linux/Unix
            roots.add(Paths.get("/usr", "lib", "jvm"));
            roots.add(Paths.get("/usr", "java"));
            roots.add(Paths.get("/opt", "java"));
        }

        return roots;
    }

    public record JvmInstallation(String displayName, Path javaExecutable) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(displayName);
            if (javaExecutable != null) {
                sb.append(" (").append(javaExecutable.toString()).append(")");
            }
            return sb.toString();
        }
    }
}
