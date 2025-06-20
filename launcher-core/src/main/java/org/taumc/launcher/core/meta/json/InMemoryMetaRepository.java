package org.taumc.launcher.core.meta.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class InMemoryMetaRepository implements MetaRepository {
    private final Map<String, PackageIndex> packageIndexes = new HashMap<>();

    private boolean initialized;

    protected final void addPackage(PackageIndex index) {
        packageIndexes.put(index.uid(), index);
    }

    protected abstract void populateRepository() throws IOException;

    private synchronized void initializeRepositoryContents() throws IOException {
        if (!initialized) {
            initialized = true;
            populateRepository();
        }
    }

    @Override
    public final RootIndex getRootIndex() throws IOException {
        this.initializeRepositoryContents();
        return new RootIndex(1, packageIndexes.values().stream().map(idx -> new RootIndex.Package(idx.name(), idx.uid())).toList());
    }

    @Override
    public final PackageIndex getPackageIndex(String pkgName) throws IOException {
        this.initializeRepositoryContents();
        var index = packageIndexes.get(pkgName);
        if (index == null) {
            throw new IOException("Package index does not exist for " + pkgName);
        }
        return index;
    }

    @Override
    public void close() {

    }
}
