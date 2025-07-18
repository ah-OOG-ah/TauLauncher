package org.taumc.launcher.core.meta.curseforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.cache.ResourceCache;
import org.taumc.launcher.core.http.DownloadThrottler;
import org.taumc.launcher.core.launch.RuntimeInstance;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.Requirement;
import org.taumc.launcher.core.mods.curseforge.File;
import org.taumc.launcher.core.mods.curseforge.Mod;
import org.taumc.launcher.core.mods.curseforge.PackManifest;
import org.taumc.launcher.core.reconciler.MissingDependenciesException;
import org.taumc.launcher.core.reconciler.ReconcilableInstance;
import org.taumc.launcher.core.reconciler.ReconciliationOptions;
import org.taumc.launcher.core.reconciler.ReconciliationResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import static org.taumc.launcher.core.meta.curseforge.CurseForgeMetaRepository.THROTTLER;

public record CurseForgeModComponent(Mod mod, File file) implements ReconcilableGameComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurseForgeModComponent.class);
    private static final ResourceCache FILES_CACHE = new ResourceCache("curseforge_files");
    private static final Methanol CLIENT = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Override
    public int order() {
        return 0;
    }

    private List<ComponentCoordinate.Simple> getMinecraftAndLoaderComponents(PackManifest manifest) {
        List<ComponentCoordinate.Simple> components = new ArrayList<>();
        components.add(new ComponentCoordinate.Simple("net.minecraft", manifest.minecraft().version()));
        Optional<PackManifest.ModLoader> loaderOptional = manifest.minecraft().modLoaders().stream().filter(PackManifest.ModLoader::primary).findFirst();
        loaderOptional.ifPresent(loader -> {
            var parts = loader.id().split("-");
            String uid = switch (parts[0]) {
                case "forge" -> "net.minecraftforge";
                case "neoforge" -> "net.neoforged";
                case "fabric" -> "net.fabricmc.fabric-loader";
                default -> throw new IllegalArgumentException(loader.id());
            };
            components.add(new ComponentCoordinate.Simple(uid, parts[1]));
        });
        return components;
    }

    private CompletableFuture<ReconciliationResult> reconcileModpack(Path zipPath, ReconcilableInstance instance, ReconciliationOptions options) {
        try (FileSystem zipfs = FileSystems.newFileSystem(zipPath, Map.of("create", "false"))) {
            Path packContentsRoot = zipfs.getRootDirectories().iterator().next();
            var manifestEntry = packContentsRoot.resolve("manifest.json");
            if (!Files.exists(manifestEntry)) {
                throw new IOException("manifest.json not in modpack");
            }
            PackManifest manifest;
            try (var is = Files.newInputStream(manifestEntry)) {
                manifest = new ObjectMapper().readValue(is, PackManifest.class);
            }
            List<Requirement> dependencies = new ArrayList<>(manifest.files().stream()
                    .filter(PackManifest.File::required)
                    .map(f -> new Requirement(CurseForgeMetaRepository.getUidFromProjectId(f.projectID()), Optional.empty(), Optional.of(String.valueOf(f.fileID()))))
                    .toList());
            dependencies.addAll(getMinecraftAndLoaderComponents(manifest).stream().map(Requirement::strict).toList());
            instance.validateRequirements(dependencies);
            Set<Path> managedPaths = new HashSet<>();
            Path minecraftFolder = instance.getBaseResolutionPath();
            var overridesFolder = packContentsRoot.resolve("overrides");
            try (Stream<Path> stream = Files.find(packContentsRoot, Integer.MAX_VALUE, (path, attrs) -> path.startsWith(overridesFolder) && !attrs.isDirectory())) {
                stream.forEach(entry -> {
                    Path entryPath = minecraftFolder.resolve(overridesFolder.relativize(entry).toString()).normalize();

                    if (instance.getInstancePath() != null && !entryPath.startsWith(minecraftFolder)) {
                        LOGGER.error("Skipping suspicious entry: {}", entry);
                        return;
                    }

                    managedPaths.add(entryPath);

                    if (instance.getInstancePath() == null) {
                        return;
                    }

                    boolean needCopy = false;

                    try {
                        if (Files.size(entryPath) != Files.size(entry)) {
                            needCopy = true;
                        }
                    } catch (NoSuchFileException e) {
                        needCopy = true;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (needCopy) {
                        try {
                            Files.createDirectories(entryPath.getParent());

                            try (var in = Files.newInputStream(entry)) {
                                Files.copy(in, entryPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }

            return CompletableFuture.completedFuture(new ReconciliationResult(managedPaths, i -> {}));
        } catch (IOException | MissingDependenciesException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<ReconciliationResult> reconcileFile(CurseForgeClass fileType, Path filePath, ReconcilableInstance instance, ReconciliationOptions options) {
        Path destinationPath = instance.getBaseResolutionPath().resolve(fileType.subfolder()).resolve(file.fileName());
        try {
            boolean needUpdate = false;
            if (instance.getInstancePath() != null) {
                try {
                    if (Files.size(destinationPath) != file.fileLength()) {
                        needUpdate = true;
                    }
                } catch (NoSuchFileException e) {
                    needUpdate = true;
                }
            }
            if (needUpdate) {
                Files.createDirectories(destinationPath.getParent());
                Files.copy(filePath, destinationPath);
            }
            return CompletableFuture.completedFuture(new ReconciliationResult(Set.of(destinationPath), i -> {}));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<ReconciliationResult> reconcile(ReconcilableInstance instance, ReconciliationOptions options) {
        var fileType = CurseForgeClass.byClassId(mod.classId());
        var downloadFuture = FILES_CACHE.computeIfAbsent(String.valueOf(file.id()), path -> Files.size(path) == file.fileLength(), destination -> {
            if (file.downloadUrl() == null || file.downloadUrl().isBlank()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("File " + file.fileName() + " lacks download URL, ID " + file.id() + " mod " + file.modId()));
            }
            return THROTTLER.throttle(() -> {
                LOGGER.info("Downloading file {}", file.fileName());
                return CLIENT.sendAsync(HttpRequest.newBuilder().uri(URI.create(file.downloadUrl())).build(), HttpResponse.BodyHandlers.ofFile(destination));
            });
        });
        return downloadFuture.thenComposeAsync(cachedFilePath -> {
            if (fileType == CurseForgeClass.MODPACKS) {
                return reconcileModpack(cachedFilePath, instance, options);
            } else {
                return reconcileFile(fileType, cachedFilePath, instance, options);
            }
        });
    }

    @Override
    public String uid() {
        return "com.curseforge.projects." + file.modId();
    }

    @Override
    public String version() {
        return String.valueOf(file.id());
    }
}
