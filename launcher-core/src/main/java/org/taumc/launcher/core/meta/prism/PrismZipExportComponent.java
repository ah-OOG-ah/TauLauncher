package org.taumc.launcher.core.meta.prism;

import com.github.mizosoft.methanol.Methanol;
import org.taumc.launcher.core.cache.ResourceCache;
import org.taumc.launcher.core.http.DownloadProgressTracker;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.reconciler.InstanceFile;
import org.taumc.launcher.core.reconciler.PathContentEntry;
import org.taumc.launcher.core.reconciler.PopulatableEntry;
import org.taumc.launcher.core.reconciler.ReconcilableInstance;
import org.taumc.launcher.core.reconciler.ReconciliationOptions;
import org.taumc.launcher.core.reconciler.ReconciliationResult;
import org.taumc.launcher.core.reconciler.exceptions.MissingDependenciesException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

public record PrismZipExportComponent(String uid, String version, URI prismExport) implements ReconcilableGameComponent {
    private static final ResourceCache DOWNLOADED_INSTANCE_CACHE = new ResourceCache("prism_export_files");
    private static final Methanol CLIENT = Methanol.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Override
    public int order() {
        return 0;
    }

    private static Path findBasePathInZip(FileSystem zipfs) throws IOException {
        Path zipRoot = zipfs.getRootDirectories().iterator().next();
        try (var stream = Files.list(zipRoot)) {
            var entries = stream.toList();

            if (entries.size() == 1 && Files.isDirectory(entries.getFirst())) {
                return entries.getFirst();
            }
        }
        return zipRoot;
    }

    @Override
    public CompletableFuture<ReconciliationResult> reconcile(ReconcilableInstance instance, ReconciliationOptions options) {
        CompletableFuture<Path> prismExportPath;
        if ("file".equals(prismExport.getScheme())) {
            prismExportPath = CompletableFuture.completedFuture(Paths.get(prismExport));
        } else {
            prismExportPath = DOWNLOADED_INSTANCE_CACHE.computeIfAbsent(uid + "." + version, destination -> {
                return DownloadProgressTracker.trackAsync(HttpResponse.BodyHandlers.ofFile(destination),
                        handler -> CLIENT.sendAsync(HttpRequest.newBuilder().uri(prismExport).build(), handler),
                        instance.getProgressProvider(),
                        "Downloading " + prismExport);
            });
        }
        return prismExportPath.thenCompose(zipPath -> {
            Map<InstanceFile, PopulatableEntry> managedPaths = new HashMap<>();
            try {
                FileSystem zipfs = FileSystems.newFileSystem(zipPath, Map.of("create", "false"));
                try {
                    Path packContentsRoot = findBasePathInZip(zipfs);
                    var mmcPack = MMCPack.read(packContentsRoot.resolve("mmc-pack.json"));
                    try (var patchesRepo = new PatchesFolderMetaRepository(packContentsRoot.resolve("patches"))) {
                        var requiredComponents = mmcPack.components().stream().map(c -> {
                            return patchesRepo.retrieveComponent(c.uid(), c.version()).handle((r, t) -> {
                                if (r != null) {
                                    return CompletableFuture.completedFuture(r);
                                } else {
                                    return instance.getMetadataService().getComponent(c);
                                }
                            }).thenCompose(Function.identity());
                        }).toList();
                        CompletableFuture.allOf(requiredComponents.toArray(new CompletableFuture[0])).join();
                        instance.validateComponents(requiredComponents.stream().map(CompletableFuture::join).toList());
                    }
                    // Relative path fragment, not the exact path
                    Path legacyMinecraftFolder = zipfs.getPath(".minecraft");
                    try (Stream<Path> stream = Files.find(packContentsRoot, Integer.MAX_VALUE, (path, attrs) -> !attrs.isDirectory())) {
                        stream.map(packContentsRoot::relativize).forEach(overrideInZip -> {
                            InstanceFile file;
                            if (overrideInZip.startsWith(legacyMinecraftFolder)) {
                                file = new InstanceFile("minecraft").resolve(InstanceFile.fromPathString(legacyMinecraftFolder.relativize(overrideInZip).toString()));
                            } else {
                                file = InstanceFile.fromPathString(overrideInZip.toString());
                            }

                            managedPaths.put(file, new PathContentEntry(packContentsRoot.resolve(overrideInZip)));
                        });
                    }
                    return CompletableFuture.completedFuture(new ReconciliationResult(managedPaths, i -> {}, zipfs));
                } catch (IOException | MissingDependenciesException e) {
                    zipfs.close();
                    throw e;
                }
            } catch (IOException | MissingDependenciesException e) {
                return CompletableFuture.failedFuture(e);
            }
        });
    }
}
