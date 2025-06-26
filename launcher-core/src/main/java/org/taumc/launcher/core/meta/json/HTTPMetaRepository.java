package org.taumc.launcher.core.meta.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.HttpCache;
import com.github.mizosoft.methanol.Methanol;
import org.taumc.launcher.core.http.HttpUtils;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;

public record HTTPMetaRepository(String url, Predicate<String> uidFilter) implements MetaRepository {
    private static final HttpCache CACHE = HttpCache.newBuilder().cacheOnDisk(LauncherPaths.getLauncherCache().resolve("caches").resolve("mmc_meta"), 100 * 1024 * 1024).build();
    private static final Methanol CLIENT = Methanol.newBuilder().cache(CACHE).build();
    private static final ObjectMapper MAPPER = JsonDecoder.make();

    public static HTTPMetaRepository prism() {
        return new HTTPMetaRepository("https://meta.prismlauncher.org/v1", null);
    }

    private static InputStream obtainFile(String url) throws IOException {
        return HttpUtils.obtainFile(CLIENT, url);
    }

    @Override
    public RootIndex getRootIndex() throws IOException {
        var rootIndex = MAPPER.readValue(obtainFile(url + "/index.json"), RootIndex.class);
        if (uidFilter == null) {
            return rootIndex;
        }
        return new RootIndex(rootIndex.formatVersion(), rootIndex.packages().stream().filter(p -> uidFilter.test(p.uid())).toList());
    }

    @Override
    public PackageIndex getPackageIndex(String pkgName) throws IOException {
        if (uidFilter != null && !uidFilter.test(pkgName)) {
            throw new IOException("Filtered");
        }
        return MAPPER.readValue(obtainFile(url + "/" + pkgName + "/index.json"), PackageIndex.class);
    }

    @Override
    public Component getComponent(String pkgName, String version) throws IOException {
        if (version == null) {
            throw new IOException("Version does not exist");
        }
        if (uidFilter != null && !uidFilter.test(pkgName)) {
            throw new IOException("Filtered");
        }
        return MAPPER.readValue(obtainFile(url + "/" + pkgName + "/" + version.replace(" ", "%20") + ".json"), Component.class);
    }

    @Override
    public void close() {

    }
}
