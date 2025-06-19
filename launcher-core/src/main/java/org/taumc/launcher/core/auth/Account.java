package org.taumc.launcher.core.auth;

import org.taumc.launcher.core.progress.ProgressProvider;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public interface Account {
    String username();
    String accessToken();
    String type();

    default UUID uuid() {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username()).getBytes(StandardCharsets.UTF_8));
    }

    default void refresh(ProgressProvider provider) throws Exception {

    }
}
