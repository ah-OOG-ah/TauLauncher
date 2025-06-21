package org.taumc.launcher.core.meta.json;

import java.io.IOException;
import java.util.List;

public class DelegatingMetaRepository implements MetaRepository {
    protected final MetaRepository delegate;

    public DelegatingMetaRepository(MetaRepository delegate) {
        this.delegate = delegate;
    }

    protected List<Requirement> transformRequirements(List<Requirement> requirements) {
        return requirements;
    }

    @Override
    public RootIndex getRootIndex() throws IOException {
        return this.delegate.getRootIndex();
    }

    @Override
    public PackageIndex getPackageIndex(String pkgName) throws IOException {
        var pkg = this.delegate.getPackageIndex(pkgName);
        return new PackageIndex(pkg.name(), pkg.uid(), pkg.versions().stream().map(v -> v.withRequires(transformRequirements(v.requires()))).toList());
    }

    @Override
    public Component getComponent(String pkgName, String version) throws IOException {
        var component = this.delegate.getComponent(pkgName, version);
        return component.toBuilder().requires(transformRequirements(component.requires())).build();
    }

    @Override
    public void close() {
        this.delegate.close();
    }
}
