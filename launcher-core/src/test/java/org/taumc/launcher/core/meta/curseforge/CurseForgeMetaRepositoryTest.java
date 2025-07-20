package org.taumc.launcher.core.meta.curseforge;

import org.junit.jupiter.api.Test;
import org.taumc.launcher.core.meta.component.ComponentSearchQuery;
import org.taumc.launcher.core.mods.ProjectType;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CurseForgeMetaRepositoryTest {
    private final CurseForgeMetaRepository repository = new CurseForgeMetaRepository();

    @Test
    void testSearch() {
        var results = repository.search(ComponentSearchQuery.builder().name("modernfix").build()).join().results();
        assertEquals(790626, CurseForgeMetaRepository.getProjectId(results.getFirst().uid()).orElseThrow());
    }

    @Test
    void testModpackSearch() {
        var results = repository.search(ComponentSearchQuery.builder().name("craftoria").projectType(ProjectType.MODPACK).build()).join().results();
        assertEquals(1039252, CurseForgeMetaRepository.getProjectId(results.getFirst().uid()).orElseThrow());
    }
}
