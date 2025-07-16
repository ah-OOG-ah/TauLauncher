package org.taumc.launcher.core.meta.prism;

import org.taumc.launcher.core.meta.component.ReconcilableGameComponent;
import org.taumc.launcher.core.meta.json.InMemoryMetaRepository;
import org.taumc.launcher.core.meta.json.Library;
import org.taumc.launcher.core.meta.json.PackageIndex;
import org.taumc.launcher.core.meta.json.Requirement;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class BuiltinMetaRepository extends InMemoryMetaRepository {
    private static final List<String> RFB_JVM_ARGS = List.of(
            "-Dfile.encoding=UTF-8",
            "-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader");
    private static final List<String> RFB_JAVA9_ARGS = List.of(
            "-Djava.security.manager=allow",
            "--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED", "--add-opens",
            "java.base/java.net=ALL-UNNAMED", "--add-opens", "java.base/java.nio=ALL-UNNAMED", "--add-opens",
            "java.base/java.io=ALL-UNNAMED", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens",
            "java.base/java.lang.reflect=ALL-UNNAMED", "--add-opens", "java.base/java.text=ALL-UNNAMED", "--add-opens",
            "java.base/java.util=ALL-UNNAMED", "--add-opens", "java.base/jdk.internal.reflect=ALL-UNNAMED", "--add-opens",
            "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED,java.naming",
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/sun.awt.image=ALL-UNNAMED",
            "--add-opens", "java.desktop/com.sun.imageio.plugins.png=ALL-UNNAMED", "--add-opens",
            "jdk.dynalink/jdk.dynalink.beans=ALL-UNNAMED", "--add-opens",
            "java.sql.rowset/javax.sql.rowset.serial=ALL-UNNAMED"
    );

    @Override
    protected void populateRepository() {
        this.addPackage(new PackageIndex("Legacy LWJGL", "org.taumc.legacy-lwjgl",
                List.of(PackageIndex.Version.simple("1.0.0", List.of(Requirement.approximately("org.lwjgl3", "3.3.3")))),
                null
        ));
        this.addPackage(new PackageIndex("RFB Launch Args", "org.taumc.rfb-args",
                Stream.of("java8", "java9").map(v -> PackageIndex.Version.simple(v, List.of())).toList(),
                null
        ));
    }

    @Override
    public CompletableFuture<ReconcilableGameComponent> retrieveComponent(String pkgName, String version) {
        return this.getPackageIndex(pkgName).thenApply(pkg -> {
            var verData = pkg.version(version).orElseThrow();
            var builder = Component.builder().packageIndex(pkg, verData);
            return switch (pkgName) {
                case "org.taumc.legacy-lwjgl" -> builder
                        .order(-5)
                        .libraries(List.of(
                                Library.fromMaven("org.taumc:legacy-lwjgl3:1.1-tau", "https://maven.taumc.org/releases")
                        ))
                        .build();
                case "org.taumc.rfb-args" -> builder
                        .extraProperty("+jvmArgs", Stream.of(RFB_JVM_ARGS, version.equals("java9") ? RFB_JAVA9_ARGS : List.of()).flatMap(Collection::stream).toList())
                        .build();
                default -> throw new RuntimeException("Unknown package " + pkgName);
            };
        });
    }
}
