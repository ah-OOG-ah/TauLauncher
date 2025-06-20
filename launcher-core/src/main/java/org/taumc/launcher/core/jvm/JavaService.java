package org.taumc.launcher.core.jvm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.lang3.ArchUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.meta.json.JsonDecoder;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class JavaService {
    private static final Path JVM_CACHE = LauncherPaths.getLauncherCache().resolve("jvm");
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaService.class);
    public static final String EXPECTED_JVM_OS = computeExpectedOs();

    private final MetadataService metadataService;
    private final ObjectMapper mapper;

    public JavaService(MetadataService metadataService) {
        this.metadataService = metadataService;
        this.mapper = JsonDecoder.make();
    }

    private static String computeExpectedOs() {
        var processor = ArchUtils.getProcessor();
        if (SystemUtils.IS_OS_WINDOWS) {
            if (processor.isAarch64()) {
                return "windows-arm64";
            } else if (processor.isX86()) {
                return processor.is64Bit() ? "windows-x64" : "windows-x86";
            }
        } else if (SystemUtils.IS_OS_MAC) {
            if (processor.isAarch64()) {
                return "mac-os-arm64";
            } else if (processor.isX86()) {
                return processor.is64Bit() ? "mac-os-x64" : "mac-os-x86";
            }
        } else if (SystemUtils.IS_OS_LINUX) {
            if (processor.isAarch64()) {
                return "linux-arm64";
            } else if (processor.isX86()) {
                return processor.is64Bit() ? "linux-x64" : "linux-x86";
            } else if ("arm".equals(System.getProperty("os.arch"))) {
                return "linux-arm32";
            }
        }
        throw new IllegalStateException("Don't know what architecture this is");
    }

    @SuppressWarnings("OctalInteger")
    private static Set<PosixFilePermission> modeToPosixPermissions(int mode) {
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);

        if ((mode & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);

        if ((mode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);

        if ((mode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);

        return perms;
    }


    private void extractJVM(Path archive, Path destination) throws IOException {
        try (
                BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(archive));
        ) {
            InputStream maybeDecompressed = bis;
            try {
                maybeDecompressed = new CompressorStreamFactory().createCompressorInputStream(bis);
            } catch (CompressorException ignored) {
                // Not compressed, use raw stream
            }
            ArchiveInputStream<? extends ArchiveEntry> tarIn = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(maybeDecompressed));

            ArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                String name = entry.getName().substring(entry.getName().indexOf('/') + 1);
                Path entryPath = destination.resolve(name).normalize();

                if (!entryPath.startsWith(destination)) {
                    throw new IOException("Bad entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Path parent = entryPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    // Create file and copy contents
                    Files.copy(tarIn, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }

                if (entry instanceof TarArchiveEntry tarEntry && Files.getFileStore(entryPath).supportsFileAttributeView("posix")) {
                    int mode = tarEntry.getMode(); // Unix mode bits from tar
                    Set<PosixFilePermission> perms = modeToPosixPermissions(mode);
                    try {
                        Files.setPosixFilePermissions(entryPath, perms);
                    } catch (UnsupportedOperationException e) {
                        // Could happen on some systems (e.g., Windows)
                    }
                }
            }
        } catch (ArchiveException e) {
            throw new IOException("Error handling archive", e);
        }
    }

    private void downloadJVM(int version, Path destination, ProgressProvider progressProvider) throws IOException, InterruptedException {
        Files.createDirectories(destination);
        var component = this.metadataService.getComponent("net.adoptium.java", "java" + version);
        if (component == null) {
            throw new IOException("Cannot find Java component for version " + version);
        }
        var runtimes = this.mapper.convertValue(component.extraProperty("runtimes"), new TypeReference<List<JavaRuntime>>() {});
        var selectedRuntime = runtimes.stream().filter(r -> EXPECTED_JVM_OS.equals(r.runtimeOS()) && r.downloadType().equals("archive")).findFirst().orElseThrow(() -> new IllegalStateException("No JVM found for current OS"));
        Path tmpDir = Files.createTempDirectory("taulauncher-java" + version);
        Path archive = tmpDir.resolve("jvm");
        try {
            LOGGER.info("Downloading JVM from {}", selectedRuntime.url());
            try (Methanol client = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                 var task = progressProvider.addTask("Downloading JVM...")) {
                var handler = DownloadProgressTracker.track(HttpResponse.BodyHandlers.ofFile(archive), task);
                var response = client.send(HttpRequest.newBuilder().GET().uri(URI.create(selectedRuntime.url())).build(), handler);
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Unexpected status code " + response.statusCode());
                }
            }
            // Extract the archive
            extractJVM(archive, destination);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static final List<String> JVM_EXECUTABLE_NAMES = List.of("javaw.exe", "java");

    public String provisionJVMBinary(int version, ProgressProvider progressProvider) throws IOException, InterruptedException {
        var jvmPath = JVM_CACHE.resolve("java" + version);
        var jvmBinPath = jvmPath.resolve("bin");
        if (JVM_EXECUTABLE_NAMES.stream().noneMatch(s -> Files.exists(jvmBinPath.resolve(s)))) {
            downloadJVM(version, jvmPath, progressProvider);
        }
        var jvmExecutable = JVM_EXECUTABLE_NAMES.stream().map(jvmBinPath::resolve).filter(Files::exists).findFirst().orElseThrow(() -> new IllegalStateException("Can't find JVM in " + jvmBinPath.toAbsolutePath()));
        return jvmExecutable.toAbsolutePath().toString();
    }
}
