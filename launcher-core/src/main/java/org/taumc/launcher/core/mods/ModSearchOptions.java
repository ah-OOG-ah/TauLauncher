package org.taumc.launcher.core.mods;

import org.taumc.launcher.core.meta.json.MMCPack;

import java.util.List;
import java.util.Optional;

public class ModSearchOptions {
    public String filterText = "";
    public List<MMCPack.Component> componentFilter;

    public Optional<String> gameVersion() {
        if (componentFilter == null) {
            return Optional.empty();
        }

        return componentFilter.stream().filter(c -> c.uid().equals("net.minecraft")).findFirst().map(MMCPack.Component::version);
    }
}
