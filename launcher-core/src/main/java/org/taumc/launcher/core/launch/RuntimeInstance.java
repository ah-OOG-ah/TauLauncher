package org.taumc.launcher.core.launch;

import com.github.mizosoft.methanol.Methanol;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.text.StringSubstitutor;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.assets.AssetService;
import org.taumc.launcher.core.auth.Account;
import org.taumc.launcher.core.auth.offline.OfflineAccount;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.meta.json.Artifact;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.Library;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.reconciler.Reconciler;
import org.taumc.launcher.core.reconciler.ReconciliationOptions;
import org.taumc.launcher.core.reconciler.intervention.ConsoleInterventionHandler;
import org.taumc.launcher.core.reconciler.tree.ComponentTreeNode;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RuntimeInstance {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeInstance.class);
    private static final Path LIBRARIES_FOLDER = LauncherPaths.getLauncherCache().resolve("libraries");
    private static final Methanol CLIENT = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private final MetadataService service = new MetadataService();
    private final AssetService assetService = new AssetService();

    @Getter
    private final ComponentTreeNode components = new ComponentTreeNode(null);
    private final List<Path> libraryPaths = new ArrayList<>();
    private final List<Path> agents = new ArrayList<>();

    private final List<Library> requestedLibraries = new ArrayList<>();
    private final List<Library> requestedMavenDownloads = new ArrayList<>();
    private final List<Library> requestedAgents = new ArrayList<>();
    private final List<String> gameArguments = new ArrayList<>();
    private final Map<String, Artifact> requestedAssetIndexes = new HashMap<>();

    private Process currentProcess;
    @Setter
    protected String mainClassName;
    @Getter
    private ProgressProvider progressProvider = ProgressProvider.NONE;

    /**
     * The root folder that Minecraft will use. The parent of this folder is guaranteed to be usable for storing
     * launcher information.
     */
    @Getter
    private Path instancePath;
    private Account launchAccount = new OfflineAccount("Dev");

    private OptionalInt minimumMemoryMB = OptionalInt.empty(), maximumMemoryMB = OptionalInt.empty();
    private List<String> extraJvmArguments = new ArrayList<>();

    private Optional<String> javaPath = Optional.empty();

    private final Map<String, String> systemProperties = new LinkedHashMap<>();
    @Getter
    private final Map<String, String> gameArgumentTemplateParameters = new HashMap<>();

    public void addComponent(ComponentCoordinate.Simple coordinate) {
        var component = this.service.getComponent(coordinate).join();
        Objects.requireNonNull(component, () -> "Component " + coordinate + " not found");
        this.components.addChild(new ComponentTreeNode(component));
    }

    public void addLibraries(Collection<Library> libraries) {
        this.requestedLibraries.addAll(libraries);
    }

    public void addMavenDownloads(Collection<Library> libraries) {
        this.requestedMavenDownloads.addAll(libraries);
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
        try {
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
                        var response = CLIENT.send(HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build(), bodyHandler);
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
                    this.downloadAndExtractNative(CLIENT, lib.extract(), nativeDiskPath, downloadUrl);
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

        var candidateLibraries = this.requestedLibraries.stream()
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

    public List<Path> getLaunchClasspath() {
        return Collections.unmodifiableList(this.libraryPaths);
    }

    private void startGame() {
        List<String> javaExecArguments = new ArrayList<>();

        javaExecArguments.add("-Duser.language=en");
        javaExecArguments.add("-Djava.library.path=" + this.instancePath.resolve("natives").toAbsolutePath().toString());

        javaExecArguments.add("-cp");
        javaExecArguments.add(this.libraryPaths.stream().map(p -> p.toAbsolutePath().toString()).distinct().collect(Collectors.joining(File.pathSeparator)));

        this.agents.forEach(p -> javaExecArguments.add("-javaagent:" + p.toAbsolutePath().toString()));

        for (var prop : this.systemProperties.entrySet()) {
            javaExecArguments.add("-D" + prop.getKey() + "=" + prop.getValue());
        }

        if (this.minimumMemoryMB.isPresent() && this.minimumMemoryMB.getAsInt() > 0) {
            javaExecArguments.add("-Xms" + this.minimumMemoryMB.getAsInt() + "M");
        }

        if (this.maximumMemoryMB.isPresent() && this.maximumMemoryMB.getAsInt() > 0) {
            javaExecArguments.add("-Xmx" + this.maximumMemoryMB.getAsInt() + "M");
        }

        javaExecArguments.addAll(this.extraJvmArguments);

        Map<String, String> templateParameters = this.gameArgumentTemplateParameters;

        Path workingDir = this.instancePath.toAbsolutePath();
        templateParameters.put("auth_player_name", launchAccount.username());
        templateParameters.put("library_directory", LIBRARIES_FOLDER.toAbsolutePath().toString());
        templateParameters.put("auth_uuid", launchAccount.uuid().toString());
        templateParameters.put("game_directory", workingDir.toString());
        templateParameters.put("assets_root", this.assetService.getAssetsRoot());
        templateParameters.put("game_assets", workingDir.resolve("resources").toString());
        templateParameters.put("auth_access_token", launchAccount.accessToken());
        templateParameters.put("auth_session", launchAccount.accessToken());
        templateParameters.put("user_properties", "{}");
        templateParameters.put("user_type", launchAccount.type());
        if (!this.requestedAssetIndexes.isEmpty()) {
            String assetIndexId = this.requestedAssetIndexes.keySet().iterator().next();
            if (this.requestedAssetIndexes.size() > 1) {
                LOGGER.warn("Using asset index {} for game arguments", assetIndexId);
            }
            templateParameters.put("assets_index_name", assetIndexId);
        }

        var subsitutor = new StringSubstitutor(templateParameters);

        List<String> gameArguments = this.gameArguments.stream().map(subsitutor::replace).toList();
        this.currentProcess = startProcess(javaExecArguments, gameArguments, workingDir);
    }

    protected void configureProcessBuilder(ProcessBuilder builder) {

    }

    protected Process startProcess(List<String> jvmArguments, List<String> gameArguments, Path workingDir) {
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(null);

        List<String> command = new ArrayList<>();
        command.add(this.javaPath.orElseThrow(() -> new IllegalArgumentException("Java path not set")));
        command.addAll(jvmArguments);
        Objects.requireNonNull(this.mainClassName, "Main class not set");
        command.add(this.mainClassName);
        command.addAll(gameArguments);

        LOGGER.info("Launching {}", String.join(" ", builder.command()).replace(launchAccount.accessToken(), "<redacted>"));
        builder.command(command);
        builder.directory(workingDir.toFile());
        configureProcessBuilder(builder);
        try {
            return builder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Process getCurrentProcess() {
        return this.currentProcess;
    }



    public void launch() throws Exception {
        if (this.components.isEmpty()) {
            throw new IllegalArgumentException("Cannot launch game with no components added");
        }

        Objects.requireNonNull(this.instancePath, "Instance path must be set");

        this.launchAccount.refresh(this.progressProvider);

        Reconciler reconciler = new Reconciler(this.components, this.getMetadataService(), this.progressProvider, new ConsoleInterventionHandler());

        try (var output = reconciler.runReconciliation(ReconciliationOptions.builder().build())) {
            output.applyToFilesystem(this.progressProvider, getInstancePath().getParent(), ReconciliationOptions.UpdateMode.UPDATE_IF_MISSING).join();
            output.configureInstance(this);
        }


        // Start asset download asynchronously
        CompletableFuture<Void> assetsFuture = CompletableFuture.allOf(this.requestedAssetIndexes.values().stream().map(a -> CompletableFuture.runAsync(() -> {
            try {
                this.assetService.downloadAssets(a, this.progressProvider);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })).toArray(CompletableFuture[]::new));

        // Compute all libraries
        var libraries = computeLibrariesToLoad();

        this.libraryPaths.clear();
        this.libraryPaths.addAll(getOrDownloadLibraries(libraries));

        // Download Maven files, but don't add them to the class path
        this.getOrDownloadLibraries(this.requestedMavenDownloads);

        this.agents.clear();
        this.agents.addAll(this.getOrDownloadLibraries(this.requestedAgents));

        // Bootstrap the game

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

    public void addExtraJvmArguments(Collection<String> extraJvmArguments) {
        this.extraJvmArguments.addAll(extraJvmArguments);
    }

    public void clearGameArguments() {
        this.gameArguments.clear();
    }

    public void addGameArgument(String gameArgument) {
        this.gameArguments.add(gameArgument);
    }

    public void addJavaAgent(Library javaAgent) {
        this.requestedAgents.add(javaAgent);
    }

    public void addAssetIndex(Artifact assetIndex) {
        String id = (String) Objects.requireNonNull(assetIndex.properties().get("id"));
        this.requestedAssetIndexes.put(id, assetIndex);
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

    public boolean hasJavaPathSet() {
        return this.javaPath.isPresent();
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = Optional.of(javaPath);
    }
}
