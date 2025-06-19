package org.taumc.launcher.core.mods;

public interface Mod {
    String name();
    String summary();
    String smallIconUrl();
    int downloadCount();
}
