package org.taumc.launcher.core.auth.offline;

import org.taumc.launcher.core.auth.Account;

public record OfflineAccount(String username) implements Account {
    @Override
    public String accessToken() {
        return "0";
    }

    @Override
    public String type() {
        return "offline";
    }
}
