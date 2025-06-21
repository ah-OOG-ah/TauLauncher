package org.taumc.launcher.core.meta.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class PatchedPrismMetaRepository extends DelegatingMetaRepository {
    private static final List<String> ASM_MODULES = List.of("asm", "asm-commons", "asm-tree", "asm-analysis", "asm-util");
    private static final String ASM_VERSION = "9.8";
    private static boolean UPGRADE_JAVA = false;

    public PatchedPrismMetaRepository() {
        super(HTTPMetaRepository.prism());
    }

    private Stream<Requirement> transformRequirement(Requirement requirement) {
        if (requirement.uid().equals("org.lwjgl")) {
            // Upgrade to LWJGL3 + legacy-lwjgl
            return Stream.of(
                    new Requirement("org.taumc.legacy-lwjgl", Optional.empty(), Optional.empty())
            );
        } else {
            return Stream.of(requirement);
        }
    }

    @Override
    protected Component.ComponentBuilder transformComponent(Component.ComponentBuilder builder, Component original) {
        builder = super.transformComponent(builder, original);
        if (original.mainClass().isPresent() && original.mainClass().get().equals("net.minecraft.launchwrapper.Launch")) {
            // Replace LaunchWrapper with RetroFuturaBootstrap for Java 9+ compat plus more powerful plugins
            builder.mainClass(Optional.of("com.gtnewhorizons.retrofuturabootstrap.Main"));
            builder.require(Requirement.strict("org.taumc.rfb-args", UPGRADE_JAVA ? "java9" : "java8"));
            List<Library> newLibraries = new ArrayList<>(original.libraries().stream().map(l -> l.name().startsWith("net.minecraft:launchwrapper:") ?
                    Library.fromMaven("com.gtnewhorizons.retrofuturabootstrap:RetroFuturaBootstrap:1.0.11", "https://nexus.gtnewhorizons.com/repository/public/") : l).toList());
            newLibraries.removeIf(lib -> lib.name().startsWith("org.ow2.asm"));
            ASM_MODULES.forEach(module -> newLibraries.add(Library.fromMaven("org.ow2.asm:" + module + ":" + ASM_VERSION, "https://libraries.minecraft.net/")));
            builder.libraries(newLibraries);
        }
        if (UPGRADE_JAVA && original.compatibleJavaMajors() != null && !original.compatibleJavaMajors().contains(21)) {
            builder.compatibleJavaMajor(21);
        }
        return builder;
    }

    @Override
    protected List<Requirement> transformRequirements(List<Requirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return requirements;
        }
        return requirements.stream().flatMap(this::transformRequirement).toList();
    }
}
