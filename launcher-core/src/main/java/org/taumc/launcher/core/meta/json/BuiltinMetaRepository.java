package org.taumc.launcher.core.meta.json;

import java.io.IOException;
import java.util.List;

public class BuiltinMetaRepository extends InMemoryMetaRepository {
    @Override
    protected void populateRepository() throws IOException {
        this.addPackage(new PackageIndex("Legacy LWJGL", "org.taumc.legacy-lwjgl",
                List.of(PackageIndex.Version.simple("1.0.0", List.of(Requirement.approximately("org.lwjgl3", "3.3.3"))))
        ));
    }

    @Override
    public Component getComponent(String pkgName, String version) throws IOException {
        var pkg = this.getPackageIndex(pkgName);
        var verData = pkg.versions().getFirst();
        return Component.builder()
                .packageIndex(pkg, verData)
                .order(-5)
                .libraries(List.of(
                       Library.fromMaven("org.taumc:legacy-lwjgl3:1.1-tau", "https://maven.taumc.org/releases")
                ))
                .build();
    }
}
