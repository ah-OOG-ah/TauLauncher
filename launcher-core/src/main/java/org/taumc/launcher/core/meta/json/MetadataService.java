package org.taumc.launcher.core.meta.json;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class MetadataService implements Closeable {
    public record DiscoveredPackageIndex(MetaRepository repo, PackageIndex index, Set<String> knownVersions) {}

    private final List<MetaRepository> repositories;
    private final Map<String, List<DiscoveredPackageIndex>> packageIndex;

    private boolean indexed = false;

    public MetadataService() {
        this.repositories = new ArrayList<>();
        this.packageIndex = new HashMap<>();
    }

    @Deprecated
    public MetadataService(String url) {
        this();
        this.addRepository(new HTTPMetaRepository(url));
        try {
            this.updateIndex();
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected error", e);
        }
    }

    private void checkIndexed() {
        if (!this.indexed) {
            throw new IllegalStateException("Metadata service has not been indexed yet");
        }
    }

    public void addRepository(MetaRepository repository) {
        this.repositories.add(repository);
        this.indexed = false;
    }

    public void updateIndex() throws IOException {
        this.packageIndex.clear();
        for (var repo : this.repositories) {
            var index = repo.getRootIndex();
            for (var pkg : index.packages()) {
                var pkgIndex = repo.getPackageIndex(pkg.uid());
                var knownVersions = pkgIndex.versions().stream().map(PackageIndex.Version::version).collect(Collectors.toCollection(LinkedHashSet::new));
                this.packageIndex.computeIfAbsent(pkg.uid(), $ -> new ArrayList<>()).add(new DiscoveredPackageIndex(repo, pkgIndex, Collections.unmodifiableSet(knownVersions)));
            }
        }
        this.indexed = true;
    }

    public Set<String> getKnownPackages() {
        this.checkIndexed();
        return Collections.unmodifiableSet(this.packageIndex.keySet());
    }

    public Set<String> getKnownVersions(String pkg) {
        this.checkIndexed();
        var indexes = this.packageIndex.getOrDefault(pkg, List.of());
        Set<String> versions = new LinkedHashSet<>();
        indexes.forEach(i -> versions.addAll(i.knownVersions()));
        return versions;
    }

    public Component getComponent(String pkg, String version) throws IOException {
        this.checkIndexed();
        var indexes = this.packageIndex.getOrDefault(pkg, List.of());
        for (var index : indexes) {
            if (version != null) {
                if (index.knownVersions().contains(version)) {
                    return index.repo().getComponent(pkg, version);
                }
            } else {
                try {
                    return index.repo().getComponent(pkg, null);
                } catch (IOException ignored) {
                }
            }
        }
        throw new IOException("Cannot find " + pkg + " version " + version);
    }

    public List<DiscoveredPackageIndex> getPackageIndexes(String pkg) {
        this.checkIndexed();
        return this.packageIndex.getOrDefault(pkg, List.of());
    }

    @Override
    public void close() {
        this.repositories.forEach(MetaRepository::close);
        this.repositories.clear();
    }
}
