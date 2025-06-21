package org.taumc.launcher.core.meta.json;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class PatchedPrismMetaRepository extends DelegatingMetaRepository {
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
    protected List<Requirement> transformRequirements(List<Requirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return requirements;
        }
        return requirements.stream().flatMap(this::transformRequirement).toList();
    }
}
