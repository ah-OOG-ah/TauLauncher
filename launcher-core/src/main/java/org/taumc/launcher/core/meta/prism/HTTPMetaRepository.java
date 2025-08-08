package org.taumc.launcher.core.meta.prism;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.CacheControl;
import com.github.mizosoft.methanol.HttpCache;
import com.github.mizosoft.methanol.Methanol;
import java.util.concurrent.ExecutionException;
import org.taumc.launcher.core.http.DownloadThrottler;
import org.taumc.launcher.core.http.JacksonBodyHandler;
import org.taumc.launcher.core.meta.component.ComponentMetaInfo;
import org.taumc.launcher.core.meta.component.ComponentSearchQuery;
import org.taumc.launcher.core.meta.component.GameComponent;
import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.JsonDecoder;
import org.taumc.launcher.core.meta.json.MetaRepository;
import org.taumc.launcher.core.meta.json.PackageIndex;
import org.taumc.launcher.core.meta.json.RootIndex;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class HTTPMetaRepository implements MetaRepository {
    private static final HttpCache CACHE = HttpCache.newBuilder().cacheOnDisk(LauncherPaths.getLauncherCache().resolve("caches").resolve("mmc_meta"), 100 * 1024 * 1024).build();
    private static final Methanol CLIENT = Methanol.newBuilder()
            .cache(CACHE)
            .defaultHeader("Cache-Control", CacheControl.newBuilder().maxStale(Duration.ofMinutes(5)).staleIfError(Duration.ofSeconds(Long.MAX_VALUE)).build().toString())
            .build();
    private static final ObjectMapper MAPPER = JsonDecoder.make();
    private static final DownloadThrottler THROTTLER = new DownloadThrottler();
    private final String url;
    private final Predicate<String> uidFilter;
    private final RootIndex rootIndex;
    private final Set<String> knownPackages;

    public HTTPMetaRepository(String url, Predicate<String> uidFilter) {
        this.url = url;
        this.uidFilter = uidFilter;
        this.rootIndex = obtainFile(url + "/index.json", new TypeReference<RootIndex>() {}).thenApply(HttpResponse::body).join();
        var stream = this.rootIndex.packages().stream().map(RootIndex.Package::uid);
        if (uidFilter != null) {
            stream = stream.filter(uidFilter);
        }
        this.knownPackages = stream.collect(Collectors.toUnmodifiableSet());
    }

    public static HTTPMetaRepository prism() {
        return new HTTPMetaRepository("https://meta.prismlauncher.org/v1", null);
    }

    private static <T> CompletableFuture<HttpResponse<T>> obtainFile(String url, TypeReference<T> ref) {
        return THROTTLER.throttle(() -> CLIENT.sendAsync(HttpRequest.newBuilder().GET().header("Accept", "application/json").uri(URI.create(url)).build(), new JacksonBodyHandler<>(ref, MAPPER)).exceptionallyCompose(e -> CompletableFuture.failedFuture(new IOException("Error downloading from " + url, e))));
    }

    @Override
    public CompletableFuture<Set<String>> getKnownPackages() {
        return CompletableFuture.completedFuture(this.knownPackages);
    }

    @Override
    public CompletableFuture<SequencedCollection<? extends GameComponent>> getKnownVersions(String pkgName, ComponentSearchQuery searchQuery) {
        return obtainFile(url + "/" + pkgName + "/index.json", new TypeReference<PackageIndex>() {
        }).thenApply(r -> {
            var versionStream = r.body().versions().stream();
            var components = searchQuery.currentComponents();
            if (components != null) {
                versionStream = versionStream.filter(v -> {
                    var requirements = v.requires();
                    for (var req : requirements) {
                        if (components.containsKey(req.uid()) && !req.isSatisfied(components)) {
                            return false;
                        }
                    }
                    return true;
                });
            }
            return versionStream.toList();
        });
    }

    @Override
    public CompletableFuture<ReconcilableGameComponent> retrieveComponent(String pkgName, String version) {
        if (version == null) {
            return CompletableFuture.failedFuture(new IOException("Version does not exist"));
        }
        if (!knownPackages.contains(pkgName)) {
            return CompletableFuture.failedFuture(new IOException("Filtered"));
        }
        return obtainFile(url + "/" + pkgName + "/" + version.replace(" ", "%20") + ".json", new TypeReference<Component>() {
        }).thenApply(r -> r.body());
    }

    @Override
    public CompletableFuture<ComponentMetaInfo> retrieveComponentMeta(String pkgName) {
        if (!knownPackages.contains(pkgName)) {
            return CompletableFuture.failedFuture(new IOException("Filtered"));
        }
        return obtainFile(url + "/" + pkgName + "/index.json", new TypeReference<PackageIndex>() {
        }).thenApply(HttpResponse::body);
    }

    @Override
    public void close() {

    }

    @Override
    public String toString() {
        return url;
    }

    // TODO: fix this hack
    public PackageIndex getMinecraftIndex() {
        try {
            return obtainFile(url + "/net.minecraft/index.json", new TypeReference<PackageIndex>() {})
                    .thenApply(HttpResponse::body).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
