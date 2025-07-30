package org.taumc.launcher.core.meta.component;

import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.taumc.launcher.core.mods.ProjectType;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

@Builder
public record ComponentSearchQuery(@Nullable String name,
                                   @Nullable String gameVersion,
                                   @Nullable Collection<String> modLoaders,
                                   @Nullable ProjectType projectType,
                                   @Nullable Map<String, ReconcilableGameComponent> currentComponents) {
    public static final ComponentSearchQuery ALL = ComponentSearchQuery.builder().build();

    @Override
    public @NotNull String toString() {
        return "component search query";
    }

    public boolean matches(ComponentMetaInfo metaInfo) {
        if (name != null && !metaInfo.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return true;
    }

    public static class ComponentSearchQueryBuilder {
        public ComponentSearchQuery build() {
            if (currentComponents != null) {
                if (gameVersion == null) {
                    var mc = currentComponents.get("net.minecraft");
                    if (mc != null) {
                        gameVersion = mc.version();
                    }
                }
                if (modLoaders == null) {
                    var loaders = new HashSet<String>();
                    for (var c : currentComponents.keySet()) {
                        switch (c) {
                            case "net.minecraftforge" -> loaders.add("forge");
                            case "net.neoforged" -> loaders.add("neoforge");
                            case "net.fabricmc.fabric-loader" -> loaders.add("fabric");
                        }
                    }
                    if (!loaders.isEmpty()) {
                        modLoaders = loaders;
                    }
                }
            }
            return new ComponentSearchQuery(
                    name,
                    gameVersion,
                    modLoaders,
                    projectType,
                    currentComponents
            );
        }
    }
}
