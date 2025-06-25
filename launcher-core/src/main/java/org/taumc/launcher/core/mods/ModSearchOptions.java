package org.taumc.launcher.core.mods;

import org.taumc.launcher.core.meta.json.MMCPack;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ModSearchOptions {
    public String filterText = "";
    public List<MMCPack.Component> componentFilter;
    public ProjectType projectType = ProjectType.MOD;

    public Stream<MMCPack.Component> componentStream() {
        if (componentFilter == null) {
            return Stream.empty();
        }
        return componentFilter.stream();
    }

    public Optional<String> gameVersion() {
        return componentStream().filter(c -> c.uid().equals("net.minecraft")).findFirst().map(MMCPack.Component::version);
    }
}
