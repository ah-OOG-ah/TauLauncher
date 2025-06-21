package org.taumc.launcher.core.meta.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MetadataService implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataService.class);
    public record DiscoveredPackageIndex(MetaRepository repo, PackageIndex index, SequencedSet<String> knownVersions) {}

    private final List<MetaRepository> repositories;
    private final Map<String, List<DiscoveredPackageIndex>> packageIndex;
    private final Map<MMCPack.Component, Component> componentCache = new ConcurrentHashMap<>();

    private boolean indexed = false;

    public MetadataService() {
        this.repositories = new ArrayList<>();
        this.packageIndex = new HashMap<>();
    }

    @Deprecated
    public MetadataService(String url) {
        this();
        this.addRepository(new HTTPMetaRepository(url, null));
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
                this.packageIndex.computeIfAbsent(pkg.uid(), $ -> new ArrayList<>()).add(new DiscoveredPackageIndex(repo, pkgIndex, Collections.unmodifiableSequencedSet(knownVersions)));
            }
        }
        this.indexed = true;
    }

    public Set<String> getKnownPackages() {
        this.checkIndexed();
        return Collections.unmodifiableSet(this.packageIndex.keySet());
    }

    public SequencedSet<String> getKnownVersions(String pkg) {
        this.checkIndexed();
        var indexes = this.packageIndex.getOrDefault(pkg, List.of());
        SequencedSet<String> versions = new LinkedHashSet<>();
        indexes.forEach(i -> versions.addAll(i.knownVersions()));
        return versions;
    }

    public final Component getComponent(ComponentCoordinate coordinate) {
        if (coordinate instanceof Component component) {
            return component;
        }
        return getComponent(coordinate.uid(), coordinate.version());
    }

    private Component findComponent(String pkg, String version) {
        var indexes = this.packageIndex.getOrDefault(pkg, List.of());
        List<IOException> errors = new ArrayList<>();
        for (var index : indexes) {
            if (version != null) {
                if (index.knownVersions().contains(version)) {
                    try {
                        return index.repo().getComponent(pkg, version);
                    } catch (IOException e) {
                        errors.add(new IOException("Could not load component " + pkg + " version " + version + " from repo " + index.repo(), e));
                    }
                }
            } else {
                try {
                    return index.repo().getComponent(pkg, null);
                } catch (IOException ignored) {
                }
            }
        }
        for (var e : errors) {
            LOGGER.error("Exception trying to load component", e);
        }
        return null;
    }

    public Component getComponent(String pkg, String version)  {
        this.checkIndexed();

        var key = new MMCPack.Component(pkg, version);
        var existing = componentCache.get(key);
        if (existing != null) {
            return existing;
        }

        existing = findComponent(pkg, version);

        if (existing != null) {
            componentCache.put(key, existing);
        }

        return existing;
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
