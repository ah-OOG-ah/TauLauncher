package org.taumc.launcher.core.mods.curseforge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import org.taumc.launcher.core.http.JacksonBodyHandler;
import org.taumc.launcher.core.meta.json.JsonDecoder;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.mods.ModSearchOptions;
import org.taumc.launcher.core.util.StreamUtils;

import java.io.Closeable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class CurseForgeAPI implements Closeable {
    private static final String API_BASE = "https://api.curseforge.com";
    // You must change this if forking the launcher.
    private static final String API_KEY = "$2a$10$i6iAcI6XXzv53fKRksUk2u1Tz8zYPHOvD0xgPxnDB/ZKKdy73xFSi";

    public static final Map<String, ModLoaderType> MOD_LOADER_TYPE_MAP = new HashMap<>();

    public static final CurseForgeAPI INSTANCE = new CurseForgeAPI();

    static {
        MOD_LOADER_TYPE_MAP.put("net.minecraftforge", ModLoaderType.FORGE);
        MOD_LOADER_TYPE_MAP.put("net.neoforged", ModLoaderType.NEOFORGE);
        MOD_LOADER_TYPE_MAP.put("net.fabricmc", ModLoaderType.FABRIC);
    }

    private final Methanol client;
    private final ObjectMapper mapper;

    public CurseForgeAPI() {
        this.client = Methanol.newBuilder().defaultHeader("x-api-key", API_KEY).build();
        this.mapper = JsonDecoder.make();
    }

    private static URI buildUri(String baseUrl) {
        return URI.create(API_BASE + baseUrl);
    }

    private static URI buildUri(String baseUrl, Map<String, ?> params) {
        String query = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        return URI.create(API_BASE + baseUrl + (baseUrl.contains("?") ? "&" : "?") + query);
    }

    private <T> CompletableFuture<HttpResponse<T>> executeQuery(URI uri, TypeReference<T> ref) {
        return this.client.sendAsync(HttpRequest.newBuilder().GET().header("Accept", "application/json").uri(uri).build(), new JacksonBodyHandler<>(ref, this.mapper));
    }

    private <T> CompletableFuture<HttpResponse<T>> executePostQuery(URI uri, Object requestBody, TypeReference<T> ref) {
        try {
            return this.client.sendAsync(HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(this.mapper.writeValueAsString(requestBody)))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .uri(uri)
                    .build(), new JacksonBodyHandler<>(ref, this.mapper));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<List<Mod>> searchForMods(ModSearchOptions modSearchOptions) {
        var params = new HashMap<String, Object>();
        params.put("gameId", "432");
        params.put("sortField", 1);
        params.put("sortOrder", "desc");
        if (!modSearchOptions.filterText.isBlank()) {
            params.put("searchFilter", modSearchOptions.filterText);
        }
        List<ModLoaderType> loaderTypes;
        if (modSearchOptions.componentFilter != null) {
            modSearchOptions.componentFilter.stream().filter(c -> c.uid().equals("net.minecraft")).findFirst().ifPresent(mc -> {
                params.put("gameVersion", mc.version());
            });
            loaderTypes = modSearchOptions.componentFilter.stream().map(c -> MOD_LOADER_TYPE_MAP.get(c.uid())).filter(Objects::nonNull).toList();
        } else {
            loaderTypes = List.of(ModLoaderType.ANY);
        }
        // Execute one search per compatible mod loader, then join the lists by mod ID to get a non-duplicated listing
        List<CompletableFuture<List<Mod>>> futures = new ArrayList<>();
        for (var type : loaderTypes) {
            if (type == ModLoaderType.ANY) {
                params.remove("modLoaderType");
            } else{
                params.put("modLoaderType", type.ordinal());
            }
            var immutableParams = Map.copyOf(params);
            futures.add(this.executeQuery(buildUri("/v1/mods/search", immutableParams), new TypeReference<PaginatedResult<Mod>>() {})
                    .thenApply(r -> r.body().data()));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply($ -> {
            return futures.stream().map(CompletableFuture::join).flatMap(Collection::stream).filter(StreamUtils.distinct(Mod::id)).toList();
        });
    }

    public CompletableFuture<String> getModDescription(int modId) {
        return this.executeQuery(buildUri("/v1/mods/" + modId + "/description"), new TypeReference<StandardResult<String>>() {})
                .thenApply(r -> r.body().data());
    }

    public CompletableFuture<Mod> getMod(int modId) {
        return this.executeQuery(buildUri("/v1/mods/" + modId), new TypeReference<StandardResult<Mod>>() {})
                .thenApply(r -> r.body().data());
    }

    public CompletableFuture<File> getModFile(int modId, int fileId) {
        return this.executeQuery(buildUri("/v1/mods/" + modId + "/files/" + fileId), new TypeReference<StandardResult<File>>() {})
                .thenApply(r -> r.body().data());
    }

    public CompletableFuture<List<File>> getFilesBulk(List<Integer> files) {
        return this.executePostQuery(buildUri("/v1/mods/files"), Map.of("fileIds", files), new TypeReference<StandardResult<List<File>>>() {})
                .thenApply(r -> r.body().data());
    }

    public CompletableFuture<List<File>> getModFiles(int modId, ModSearchOptions modSearchOptions) {
        var params = new HashMap<String, Object>();
        List<ModLoaderType> loaderTypes;
        if (modSearchOptions.componentFilter != null) {
            modSearchOptions.componentFilter.stream().filter(c -> c.uid().equals("net.minecraft")).findFirst().ifPresent(mc -> {
                params.put("gameVersion", mc.version());
            });
            loaderTypes = modSearchOptions.componentFilter.stream().map(c -> MOD_LOADER_TYPE_MAP.get(c.uid())).filter(Objects::nonNull).toList();
        } else {
            loaderTypes = List.of(ModLoaderType.ANY);
        }
        // Execute one search per compatible mod loader, then join the lists by mod ID to get a non-duplicated listing
        List<CompletableFuture<List<File>>> futures = new ArrayList<>();
        for (var type : loaderTypes) {
            if (type == ModLoaderType.ANY) {
                params.remove("modLoaderType");
            } else{
                params.put("modLoaderType", type.ordinal());
            }
            var immutableParams = Map.copyOf(params);
            futures.add(this.executeQuery(buildUri("/v1/mods/" + modId + "/files", immutableParams), new TypeReference<PaginatedResult<File>>() {})
                    .thenApply(r -> r.body().data()));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply($ -> {
            return futures.stream().map(CompletableFuture::join).flatMap(Collection::stream).filter(f -> f.downloadUrl() != null).filter(StreamUtils.distinct(File::id)).sorted(Comparator.comparing(File::fileDate).reversed()).toList();
        });
    }

    public CompletableFuture<FingerprintMatches> getFingerprintMatches(List<Integer> murmur2Hashes) {
        return this.executePostQuery(buildUri("/v1/fingerprints"), new GetFingerprintMatchesRequest(murmur2Hashes.stream().map(Integer::toUnsignedLong).toList()), new TypeReference<StandardResult<FingerprintMatches>>() {})
                .thenApply(r -> r.body().data());
    }

    @Override
    public void close() {
        this.client.close();
    }
}
