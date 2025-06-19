package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.HttpCache;
import com.github.mizosoft.methanol.Methanol;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public record HTTPMetaRepository(String url) implements MetaRepository {
    private static final HttpCache CACHE = HttpCache.newBuilder().cacheOnDisk(LauncherPaths.getLauncherCache().resolve("meta_cache"), 100 * 1024 * 1024).build();
    private static final Methanol CLIENT = Methanol.newBuilder().cache(CACHE).build();
    private static final ObjectMapper MAPPER = JsonDecoder.make();

    public static HTTPMetaRepository prism() {
        return new HTTPMetaRepository("https://meta.prismlauncher.org/v1");
    }

    private static InputStream obtainFile(String url) throws IOException {
        HttpResponse<InputStream> response;
        try {
            response = CLIENT.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading");
        }
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new IOException("Unexpected response status for " + url + ": " + response.statusCode());
        }
    }

    @Override
    public RootIndex getRootIndex() throws IOException {
        return MAPPER.readValue(obtainFile(url + "/index.json"), RootIndex.class);
    }

    @Override
    public PackageIndex getPackageIndex(String pkgName) throws IOException {
        return MAPPER.readValue(obtainFile(url + "/" + pkgName + "/index.json"), PackageIndex.class);
    }

    @Override
    public Component getComponent(String pkgName, String version) throws IOException {
        if (version == null) {
            throw new IOException("Version does not exist");
        }
        return MAPPER.readValue(obtainFile(url + "/" + pkgName + "/" + version + ".json"), Component.class);
    }

    @Override
    public void close() {

    }
}
