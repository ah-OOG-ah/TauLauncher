package org.taumc.launcher.core.launch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.apache.commons.text.StringSubstitutor;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.assets.AssetService;
import org.taumc.launcher.core.auth.Account;
import org.taumc.launcher.core.auth.offline.OfflineAccount;
import org.taumc.launcher.core.components.BuiltinComponents;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.jvm.JavaService;
import org.taumc.launcher.core.meta.json.Artifact;
import org.taumc.launcher.core.meta.json.Component;
import org.taumc.launcher.core.meta.json.Library;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.meta.json.Requirement;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RuntimeInstance {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeInstance.class);
    private static final Path LIBRARIES_FOLDER = LauncherPaths.getLauncherCache().resolve("libraries");

    private final MetadataService service = new MetadataService();
    private final AssetService assetService = new AssetService();
    private final JavaService javaService = new JavaService(service);
    private final List<Component> components = new ArrayList<>();

    private final List<Path> libraryPaths = new ArrayList<>();
    private final List<Path> agents = new ArrayList<>();

    private Optional<Artifact> assetIndex;
    private Path mainJarPath;
    private Process currentProcess;
    private String mainClassName;
    private Component mainComponent;
    private ProgressProvider progressProvider = ProgressProvider.NONE;

    // TODO
    private Path instancePath;
    private Account launchAccount = new OfflineAccount("Dev");

    private CompletableFuture<String> javaBinaryFuture;

    private OptionalInt minimumMemoryMB = OptionalInt.empty(), maximumMemoryMB = OptionalInt.empty();
    private List<String> extraJvmArguments = new ArrayList<>();

    private final Map<String, String> systemProperties = new LinkedHashMap<>();

    public void addComponent(MMCPack.Component coordinate) throws IOException, InterruptedException {
        var component = this.service.getComponent(coordinate.uid(), coordinate.version());
        if (component == null) {
            throw new NullPointerException("Component " + coordinate + " does not exist in any meta repositories");
        }
        addComponent(component);
    }

    public void addComponent(Component component) {
        this.components.add(component);
    }

    public void addComponents(List<MMCPack.Component> components) throws IOException, InterruptedException {
        for (var component : components) {
            this.addComponent(component);
        }
    }

    public void setLaunchAccount(Account account) {
        this.launchAccount = account;
    }

    private void downloadAndExtractNative(HttpClient httpClient, Library.ExtractConfig extractConfig, Path diskPath, String downloadUrl) throws IOException, InterruptedException {
        if (!Files.exists(diskPath)) {
            LOGGER.info("Trying to download {}", diskPath.getFileName().toString());
            var response = httpClient.send(HttpRequest.newBuilder()
                            .uri(URI.create(downloadUrl)).GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofFile(diskPath));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(diskPath);
                throw new IOException("Failed to download from URL " + downloadUrl);
            }
        }
        var nativesFolder = this.instancePath.resolve("natives");
        Files.createDirectories(nativesFolder);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(diskPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("META-INF/")) {
                    continue;
                }
                boolean shouldCopy = true;
                if (extractConfig != null) {
                    for (var exclude : extractConfig.exclude()) {
                        if (name.startsWith(exclude)) {
                            shouldCopy = false;
                            break;
                        }
                    }
                }
                if (!shouldCopy) {
                    continue;
                }
                Path nativePath = nativesFolder.resolve(entry.getName());
                Files.createDirectories(nativePath.getParent());
                try (OutputStream os = Files.newOutputStream(nativePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    zis.transferTo(os);
                }
            }
        }
    }

    private Path downloadLibrary(Library lib) {
        if ("local".equals(lib.mmcHint())) {
            var fileName = lib.mmcFileName();
            if (fileName == null) {
                fileName = lib.explodedName().fileName();
            }
            Path libPath = this.instancePath.getParent().resolve("libraries").resolve(fileName);
            if (!Files.exists(libPath)) {
                throw new IllegalArgumentException("Library " + lib.name() + " is specified as being local but does not exist in instance folder");
            }
            return libPath;
        }
        var diskPath = LIBRARIES_FOLDER.resolve(lib.diskPath());
        try (var httpClient = Methanol.newBuilder().build()) {
            var download = lib.findDownload();
            Path classPathEntry = download.artifact() != null ? diskPath : null;
            if (!Files.exists(diskPath)) {
                Files.createDirectories(diskPath.getParent());
                // Try to download
                if (download.artifact() != null) {
                    LOGGER.info("Trying to download {}", lib.name());
                    String downloadUrl = lib.findDownload().artifact().url();
                    try (var task = progressProvider.addTask("Downloading " + lib.name() + "...")) {
                        var bodyHandler = DownloadProgressTracker.track(HttpResponse.BodyHandlers.ofFile(diskPath), task);
                        var response = httpClient.send(HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build(), bodyHandler);
                        if (response.statusCode() != 200) {
                            Files.deleteIfExists(diskPath);
                            throw new IOException("Failed to download from URL " + downloadUrl);
                        }
                    }
                }
            }
            // Also download the appropriate natives and extract them to the natives dir
            if (lib.natives() != null) {
                var nativeClassifier = lib.natives().get(Library.NATIVE_CATEGORY);
                if (nativeClassifier != null) {
                    var nativeName = lib.explodedName().withClassifier(nativeClassifier);
                    Path nativeDiskPath = LIBRARIES_FOLDER.resolve(nativeName.diskPath());
                    String downloadUrl = lib.downloads().orElseThrow().classifiers().get(nativeClassifier).url();
                    this.downloadAndExtractNative(httpClient, lib.extract(), nativeDiskPath, downloadUrl);
                }
            }
            return classPathEntry;
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to download library {}", lib.name(), e);
            throw new RuntimeException(e);
        }
    }

    private List<Path> getOrDownloadLibraries(Collection<Library> libraries) {
        return libraries.parallelStream().map(this::downloadLibrary).filter(Objects::nonNull).toList();
    }

    private List<Library> computeLibrariesToLoad() {
        record GroupAndName(String group, String name) { }

        Function<Library, GroupAndName> keyMapper = l -> {
            var e = l.explodedName();
            return new GroupAndName(e.group(), e.name());
        };

        var candidateLibraries = this.components.stream().filter(c -> c.libraries() != null)
                .flatMap(c -> c.libraries().stream())
                .filter(l -> l.rules() == null || l.rules().stream().allMatch(Library.Rule::passes))
                .toList();

        Map<GroupAndName, String> biggestSeenVersion = new HashMap<>();

        for (var lib : candidateLibraries) {
            var e = lib.explodedName();
            var key = new GroupAndName(e.group(), e.name());
            biggestSeenVersion.compute(key, (k, oldVersion) -> {
                boolean isNewer = oldVersion == null || new DefaultArtifactVersion(oldVersion).compareTo(new DefaultArtifactVersion(e.version())) <= 0;
                return isNewer ? e.version() : oldVersion;
            });
        }

        return candidateLibraries.stream().filter(lib -> {
            var biggestVer = biggestSeenVersion.get(keyMapper.apply(lib));
            if (!lib.explodedName().version().equals(biggestVer)) {
                LOGGER.warn("Skipping library {} as another component adds a newer version {}", lib.name(), biggestVer);
                return false;
            } else {
                return true;
            }
        }).toList();
    }

    private void scanDependencies() throws IOException, InterruptedException {
        while (true) {
            List<Requirement> requirementsToFix = List.of();
            Component complainingComponent = null;
            for (var component : this.components) {
                if (component.requires() == null) {
                    continue;
                }
                var missingDeps = component.requires().stream().filter(r -> !r.isSatisfied(this.components, this.getMetadataService())).toList();
                if (!missingDeps.isEmpty()) {
                    requirementsToFix = missingDeps;
                    complainingComponent = component;
                    break;
                }
            }
            if (requirementsToFix.isEmpty()) {
                break;
            }
            for (var r : requirementsToFix) {
                var component = this.components.stream().filter(c -> c.uid().equals(r.uid())).findFirst();
                if (component.isPresent()) {
                    throw new IllegalStateException("Component " + complainingComponent.uid() + " has requirement " + r + ", but version " + component.get().version() + " is already added");
                }
                String version = r.recommendedVersion().orElseGet(() -> this.getMetadataService().getKnownVersions(r.uid()).getLast());
                LOGGER.info("Adding missing component {} with version {}", r.uid(), version);
                this.addComponent(new MMCPack.Component(r.uid(), version));
            }
        }
    }

    private void extractBootstrap() throws IOException {
        Path bootstrapFile = LIBRARIES_FOLDER.resolve(Paths.get("org", "taumc", "launcher", "bootstrap", "bootstrap.jar"));
        Files.createDirectories(bootstrapFile.getParent());
        try (var is = RuntimeInstance.class.getResourceAsStream("/taulauncher-bootstrap/taulauncher-bootstrap.jar")) {
            if (is == null) {
                throw new FileNotFoundException("Bootstrap jar not found where expected");
            }
            Files.copy(is, bootstrapFile, StandardCopyOption.REPLACE_EXISTING);
        }
        this.libraryPaths.add(bootstrapFile);
    }

    private CompletableFuture<String> computeJavaVersion() {
        int javaVersion;

        if (this.mainComponent.compatibleJavaMajors() != null) {
            javaVersion = this.mainComponent.compatibleJavaMajors().stream().mapToInt(Integer::intValue).max().orElseThrow();
        } else {
            javaVersion = 21; // Default
        }

        LOGGER.info("Selected Java version {} for main component {} version {}", javaVersion, this.mainComponent.name(), this.mainComponent.version());

        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.javaService.provisionJVMBinary(javaVersion, this.progressProvider);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Path> getLaunchClasspath() {
        return Collections.unmodifiableList(this.libraryPaths);
    }

    private void startGame() {
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(null);

        List<String> command = new ArrayList<>();
        command.add(this.javaBinaryFuture.join());

        command.add("-Duser.language=en");
        command.add("-Djava.library.path=" + this.instancePath.resolve("natives").toAbsolutePath().toString());

        command.add("-cp");
        command.add(this.libraryPaths.stream().map(p -> p.toAbsolutePath().toString()).distinct().collect(Collectors.joining(File.pathSeparator)) + File.pathSeparator + this.mainJarPath.toAbsolutePath().toString());

        this.components.stream().map(c -> c.extraProperty("+jvmArgs")).filter(Objects::nonNull).forEach(extraArgs -> {
            command.addAll((List<String>)extraArgs);
        });

        this.agents.forEach(p -> command.add("-javaagent:" + p.toAbsolutePath().toString()));

        for (var prop : this.systemProperties.entrySet()) {
            command.add("-D" + prop.getKey() + "=" + prop.getValue());
        }

        if (this.minimumMemoryMB.isPresent() && this.minimumMemoryMB.getAsInt() > 0) {
            command.add("-Xms" + this.minimumMemoryMB.getAsInt() + "M");
        }

        if (this.maximumMemoryMB.isPresent() && this.maximumMemoryMB.getAsInt() > 0) {
            command.add("-Xmx" + this.maximumMemoryMB.getAsInt() + "M");
        }

        command.addAll(this.extraJvmArguments);

        command.add(this.mainClassName);

        Map<String, String> templateParameters = new HashMap<>();

        Path workingDir = this.instancePath.toAbsolutePath();
        templateParameters.put("auth_player_name", launchAccount.username());
        templateParameters.put("library_directory", LIBRARIES_FOLDER.toAbsolutePath().toString());
        templateParameters.put("auth_uuid", launchAccount.uuid().toString());
        templateParameters.put("game_directory", workingDir.toString());
        templateParameters.put("assets_root", this.assetService.getAssetsRoot());
        templateParameters.put("game_assets", workingDir.resolve("resources").toString());
        templateParameters.put("auth_access_token", launchAccount.accessToken());
        templateParameters.put("auth_session", launchAccount.accessToken());
        templateParameters.put("version_name", this.mainComponent.version());
        templateParameters.put("version_type", (String)this.mainComponent.extraProperties().get("type"));
        templateParameters.put("user_properties", "{}");
        templateParameters.put("user_type", launchAccount.type());
        this.assetIndex.ifPresent(a -> templateParameters.put("assets_index_name", (String)a.properties().get("id")));

        var subsitutor = new StringSubstitutor(templateParameters);

        Optional<String> minecraftArguments = this.components.stream()
                .map(c -> (String)c.extraProperty("minecraftArguments"))
                .filter(Objects::nonNull)
                .reduce((f, s) -> s);

        if (minecraftArguments.isPresent()) {
            for(String arg :minecraftArguments.get().split(" ")) {
                command.add(subsitutor.replace(arg));
            }
        }

        this.components.stream()
                .map(c -> c.extraProperty("+tweakers"))
                .filter(Objects::nonNull)
                .flatMap(l -> ((List<String>)l).stream())
                .forEach(t -> {
                    command.add("--tweakClass");
                    command.add(t);
                });

        builder.command(command);
        builder.directory(workingDir.toFile());
        LOGGER.info("Launching {}", String.join(" ", builder.command()).replace(launchAccount.accessToken(), "<redacted>"));
        try {
            this.currentProcess = builder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Process getCurrentProcess() {
        return this.currentProcess;
    }

    public void launch() throws Exception {
        Objects.requireNonNull(this.instancePath, "Instance path must be set");

        this.launchAccount.refresh(this.progressProvider);

        this.scanDependencies();

        var traits = this.components.stream().map(Component::traits).filter(Objects::nonNull).flatMap(Collection::stream).distinct().toList();

        boolean isLegacyLaunch = (traits.contains("legacyLaunch") || traits.contains("alphaLaunch")) && !traits.contains("noapplet");

        if (isLegacyLaunch) {
            this.addComponent(BuiltinComponents.LEGACY_LAUNCH_WRAPPER);
        }

        this.components.sort(Comparator.comparingInt(Component::order));

        // Start asset download asynchronously
        CompletableFuture<Void> assetsFuture;
        this.assetIndex = this.components.stream().flatMap(c -> c.assetIndex().stream()).findFirst();
        if (this.assetIndex.isPresent()) {
            assetsFuture = CompletableFuture.runAsync(() -> {
                try {
                    this.assetService.downloadAssets(this.assetIndex.get(), this.progressProvider);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            assetsFuture = CompletableFuture.completedFuture(null);
        }

        // Compute all libraries
        var libraries = computeLibrariesToLoad();

        this.libraryPaths.clear();
        this.libraryPaths.addAll(getOrDownloadLibraries(libraries));

        // Download Maven files, but don't add them to the class path
        this.getOrDownloadLibraries(this.components.stream().map(Component::mavenFiles).filter(Objects::nonNull).flatMap(Collection::stream).toList());

        this.agents.clear();
        this.agents.addAll(this.getOrDownloadLibraries(this.components.stream().map(Component::agents).filter(Objects::nonNull).flatMap(Collection::stream).toList()));

        // Bootstrap the game
        this.mainComponent = this.components.stream().filter(c -> c.mainJar().isPresent()).findFirst().orElseThrow(() -> new IllegalStateException("Main jar does not exist in any components"));
        this.mainJarPath = this.downloadLibrary(this.mainComponent.mainJar().orElseThrow());

        this.mainClassName = this.components.stream().flatMap(c -> c.mainClass() != null ? c.mainClass().stream() : Stream.empty()).reduce((first, second) -> second).orElseThrow(() -> new IllegalStateException("No component has a main class"));

        this.javaBinaryFuture = this.computeJavaVersion();

        // Wait for assets
        assetsFuture.join();

        this.startGame();
    }

    public void setProgressProvider(ProgressProvider progressProvider) {
        this.progressProvider = progressProvider;
    }

    public void setMinimumMemoryMB(int minimumMemoryMB) {
        this.minimumMemoryMB = OptionalInt.of(minimumMemoryMB);
    }

    public void setMaximumMemoryMB(int maximumMemoryMB) {
        this.maximumMemoryMB = OptionalInt.of(maximumMemoryMB);
    }

    public void setExtraJvmArguments(List<String> extraJvmArguments) {
        this.extraJvmArguments.clear();
        this.extraJvmArguments.addAll(extraJvmArguments);
    }

    public void setInstancePath(Path instancePath) {
        if (!Files.isDirectory(instancePath)) {
            throw new IllegalArgumentException();
        }
        this.instancePath = instancePath;
    }

    public MetadataService getMetadataService() {
        return this.service;
    }
}
