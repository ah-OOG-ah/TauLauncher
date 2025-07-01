package org.taumc.launcher.core.mods.modpacksch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.taumc.launcher.core.http.JacksonBodyHandler;
import org.taumc.launcher.core.http.URIBuilder;
import org.taumc.launcher.core.meta.json.JsonDecoder;
import org.taumc.launcher.core.mods.ModHostingSite;
import org.taumc.launcher.core.mods.ModSearchOptions;
import org.taumc.launcher.core.mods.ModUpdate;
import org.taumc.launcher.core.mods.ProjectType;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModpacksCHHostingSite implements ModHostingSite<Modpack, DownloadableModpackVersion> {
    private final Methanol client;
    private final ObjectMapper mapper;
    private static final URIBuilder API_BUILDER = new URIBuilder("https://api.modpacks.ch/public");

    public ModpacksCHHostingSite() {
        this.mapper = JsonDecoder.make();
        this.client = Methanol.newBuilder().userAgent("TauLauncher/" + System.getProperty("taulauncher.version")).build();
    }

    @Override
    public Collection<ProjectType> getProjectTypes() {
        return Set.of(ProjectType.MODPACK);
    }

    @Override
    public String name() {
        return "modpacks.ch";
    }

    private <T> CompletableFuture<HttpResponse<T>> executeQuery(URI uri, TypeReference<T> ref) {
        return this.client.sendAsync(HttpRequest.newBuilder().GET().header("Accept", "application/json").uri(uri).build(), new JacksonBodyHandler<>(ref, this.mapper));
    }

    @Override
    public CompletableFuture<List<Modpack>> searchForMods(ModSearchOptions searchOptions) {
        if (searchOptions.projectType != ProjectType.MODPACK) {
            return CompletableFuture.completedFuture(List.of());
        }
        CompletableFuture<HttpResponse<PackListing>> future;
        if (searchOptions.filterText.isBlank()) {
            future = executeQuery(API_BUILDER.buildUri("/modpack/featured/10"), new TypeReference<>() {});
        } else {
            future = executeQuery(API_BUILDER.buildUri("/modpack/search/10", Map.of("term", searchOptions.filterText)), new TypeReference<>() {});
        }
        return future.thenCompose(res -> {
            var listing = res.body();
            List<CompletableFuture<HttpResponse<Modpack>>> modpackFutures = listing.packs().stream()
                    .limit(10)
                    .map(id -> executeQuery(API_BUILDER.buildUri("/modpack/" + id), new TypeReference<Modpack>() {}))
                    .toList();
            return CompletableFuture.allOf(modpackFutures.toArray(new CompletableFuture[0])).thenApply($ -> modpackFutures.stream().map(CompletableFuture::join).map(HttpResponse::body).toList());
        });
    }

    @Override
    public CompletableFuture<List<DownloadableModpackVersion>> getModFiles(Modpack mod, ModSearchOptions searchOptions) {
        return CompletableFuture.completedFuture(mod.getDownloadableFiles());
    }

    @Override
    public CompletableFuture<List<DownloadableModpackVersion>> getDependencies(DownloadableModpackVersion file, ModSearchOptions searchOptions) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<ModUpdate>> getModUpdates(List<Path> modFiles, ModSearchOptions searchOptions, ProgressProvider progressProvider) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<String> getModDescriptionHTML(Modpack mod) {
        return CompletableFuture.supplyAsync(() -> {
            Parser parser = Parser.builder().build();
            Node document = parser.parse(mod.description());
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            return renderer.render(document);
        });
    }
}
