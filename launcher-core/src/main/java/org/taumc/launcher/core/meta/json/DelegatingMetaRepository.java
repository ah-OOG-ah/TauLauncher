package org.taumc.launcher.core.meta.json;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

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
        return new PackageIndex(pkg.name(), pkg.uid(), pkg.versions().stream().map(v -> v.withRequires(transformRequirements(v.requires()))).toList(), null);
    }

    protected Component.ComponentBuilder transformComponent(Component.ComponentBuilder builder, Component original) {
        return builder.clearRequires().requires(transformRequirements(Objects.requireNonNullElse(original.requires(), List.of())));
    }

    @Override
    public final Component getComponent(String pkgName, String version) throws IOException {
        var component = this.delegate.getComponent(pkgName, version);
        return transformComponent(component.toBuilder(), component).build();
    }

    @Override
    public void close() {
        this.delegate.close();
    }
}
