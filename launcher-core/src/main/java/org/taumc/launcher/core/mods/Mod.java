package org.taumc.launcher.core.mods;

public interface Mod {
    String name();
    String modId();
    String summary();
    String smallIconUrl();
    int downloadCount();
}
