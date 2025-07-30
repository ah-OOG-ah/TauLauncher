package org.taumc.launcher.core.meta.curseforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.cache.ResourceCache;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.Requirement;
import org.taumc.launcher.core.mods.curseforge.File;
import org.taumc.launcher.core.mods.curseforge.Mod;
import org.taumc.launcher.core.mods.curseforge.PackManifest;
import org.taumc.launcher.core.reconciler.InstanceFile;
import org.taumc.launcher.core.reconciler.exceptions.MissingDependenciesException;
import org.taumc.launcher.core.reconciler.PathPopulator;
import org.taumc.launcher.core.reconciler.ReconcilableInstance;
import org.taumc.launcher.core.reconciler.ReconciliationOptions;
import org.taumc.launcher.core.reconciler.ReconciliationResult;
import org.taumc.launcher.core.reconciler.exceptions.UserInterventionRequiredException;
import org.taumc.launcher.core.reconciler.intervention.ManualDownloadIntervention;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.taumc.launcher.core.meta.curseforge.CurseForgeMetaRepository.THROTTLER;
import static org.taumc.launcher.core.reconciler.ReconciliationHelpers.fileNeedsUpdate;

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
        try {
            FileSystem zipfs = FileSystems.newFileSystem(zipPath, Map.of("create", "false"));
            try {
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
                Map<InstanceFile, PathPopulator> managedPaths = new HashMap<>();
                var overridesFolder = packContentsRoot.resolve("overrides");
                try (Stream<Path> stream = Files.find(packContentsRoot, Integer.MAX_VALUE, (path, attrs) -> path.startsWith(overridesFolder) && !attrs.isDirectory())) {
                    stream.forEach(overrideInZip -> {
                        InstanceFile file = InstanceFile.fromPathString(overridesFolder.relativize(overrideInZip).toString());

                        managedPaths.put(file, targetOnDisk -> {
                            if (fileNeedsUpdate(overrideInZip, targetOnDisk, options.updateMode())) {
                                Files.createDirectories(targetOnDisk.getParent());

                                try (var in = Files.newInputStream(overrideInZip)) {
                                    Files.copy(in, targetOnDisk, StandardCopyOption.REPLACE_EXISTING);
                                }
                            }
                        });
                    });
                }

                return CompletableFuture.completedFuture(new ReconciliationResult(managedPaths, i -> {}, zipfs));
            } catch (IOException e) {
                zipfs.close();
                throw e;
            }
        } catch (IOException | MissingDependenciesException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<ReconciliationResult> reconcileFile(CurseForgeClass fileType, Path cfFilePath, ReconcilableInstance instance, ReconciliationOptions options) {
        InstanceFile destinationFile = new InstanceFile(fileType.subfolder()).resolve(file.fileName());
        return CompletableFuture.completedFuture(new ReconciliationResult(Map.of(destinationFile, destinationPath -> {
            if (fileNeedsUpdate(cfFilePath, destinationPath, options.updateMode())) {
                Files.createDirectories(destinationPath.getParent());
                Files.copy(cfFilePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }), i -> {}, null));
    }

    @Override
    public CompletableFuture<ReconciliationResult> reconcile(ReconcilableInstance instance, ReconciliationOptions options) {
        var fileType = CurseForgeClass.byClassId(mod.classId());
        var downloadFuture = FILES_CACHE.computeIfAbsent(String.valueOf(file.id()), path -> Files.size(path) == file.fileLength(), destination -> {
            if (file.downloadUrl() == null || file.downloadUrl().isBlank()) {
                String manualDownloadUrl = "https://legacy.curseforge.com/minecraft/" + fileType.urlSlug() + "/" + mod.slug() + "/download/" + file.id();
                return CompletableFuture.failedFuture(new UserInterventionRequiredException(ManualDownloadIntervention.forUrl(manualDownloadUrl, file.fileName(), file.fileLength(), destination)));
            }
            return THROTTLER.throttle(() -> {
                LOGGER.info("Downloading file {}", file.fileName());
                return DownloadProgressTracker.trackAsync(HttpResponse.BodyHandlers.ofFile(destination),
                        handler -> CLIENT.sendAsync(HttpRequest.newBuilder().uri(URI.create(file.downloadUrl())).build(), handler),
                        instance.getProgressProvider(),
                        "Downloading " + file.fileName());
            });
        });
        return downloadFuture.thenComposeAsync(cachedFilePath -> {
            if (fileType == CurseForgeClass.MODPACK) {
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

    @Override
    public String name() {
        return file.fileName();
    }

    @Override
    public String friendlyVersion() {
        return "";
    }

    @Override
    public @NotNull String toString() {
        return file.fileName();
    }
}
