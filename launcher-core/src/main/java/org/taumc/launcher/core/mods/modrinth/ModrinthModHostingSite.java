package org.taumc.launcher.core.mods.modrinth;

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
import org.taumc.launcher.core.progress.ProgressProvider;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ModrinthModHostingSite implements ModHostingSite<Project, Version> {
    private final Methanol client;
    private final ObjectMapper mapper;
    private static final URIBuilder API_BUILDER = new URIBuilder("https://api.modrinth.com/v2");
    private static final Map<String, String> MOD_LOADER_MAP = Map.of(
            "net.ornithemc.intermediary", "ornithe",
            "net.minecraftforge", "forge",
            "net.neoforged", "neoforge",
            "net.fabricmc", "fabric"
    );

    public ModrinthModHostingSite() {
        this.mapper = JsonDecoder.make();
        this.client = Methanol.newBuilder().userAgent("TauLauncher/0.1.0").build();
    }

    @Override
    public String name() {
        return "Modrinth";
    }

    private <T> CompletableFuture<HttpResponse<T>> executeQuery(URI uri, TypeReference<T> ref) {
        return this.client.sendAsync(HttpRequest.newBuilder().GET().header("Accept", "application/json").uri(uri).build(), new JacksonBodyHandler<>(ref, this.mapper));
    }

    @Override
    public CompletableFuture<List<Project>> searchForMods(ModSearchOptions searchOptions) {
        List<String> facets = new ArrayList<>();
        facets.add("project_type:mod");
        searchOptions.gameVersion().ifPresent(v -> facets.add("versions:" + v));
        var loaders = "[" + searchOptions.componentStream().map(c -> MOD_LOADER_MAP.get(c.uid())).filter(Objects::nonNull).map(s -> "\"categories:" + s + "\"").collect(Collectors.joining(", ")) + "]";
        String facetParam = "[" + loaders + "," + facets.stream().map(s -> "[\"" + s + "\"]").collect(Collectors.joining(",")) + "]";
        return this.executeQuery(API_BUILDER.buildUri("/search", Map.of("query", searchOptions.filterText, "facets", facetParam)), new TypeReference<PaginatedResult<Project>>() {})
                .thenApply(r -> r.body().hits());
    }

    @Override
    public CompletableFuture<List<Version>> getModFiles(Project mod, ModSearchOptions searchOptions) {
        var loaders = "[" + searchOptions.componentStream().map(c -> MOD_LOADER_MAP.get(c.uid())).filter(Objects::nonNull).map(s -> "\"" + s + "\"").collect(Collectors.joining(",")) + "]";
        var params = new HashMap<String, Object>();
        params.put("loaders", loaders);
        searchOptions.gameVersion().ifPresent(v -> params.put("game_versions", "[\"%s\"]".formatted(v)));
        return this.executeQuery(API_BUILDER.buildUri("/project/" + mod.project_id() + "/version", params), new TypeReference<List<Version>>() {})
                .thenApply(r -> r.body().stream().filter(v -> !v.files().isEmpty()).toList());
    }

    @Override
    public CompletableFuture<List<Version>> getDependencies(Version file, ModSearchOptions searchOptions) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<ModUpdate>> getModUpdates(List<Path> modFiles, ModSearchOptions searchOptions, ProgressProvider progressProvider) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<String> getModDescriptionHTML(Project mod) {
        return this.executeQuery(API_BUILDER.buildUri("/project/" + mod.project_id()), new TypeReference<Map<String, Object>>() {})
                .thenApply(r -> {
                    String body = (String)r.body().getOrDefault("body", "");
                    Parser parser = Parser.builder().build();
                    Node document = parser.parse(body);
                    HtmlRenderer renderer = HtmlRenderer.builder().build();
                    return renderer.render(document);
                });
    }
}
