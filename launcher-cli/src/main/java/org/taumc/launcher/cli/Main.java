package org.taumc.launcher.cli;

import org.taumc.launcher.core.auth.AccountService;
import org.taumc.launcher.core.auth.microsoft.MicrosoftAccount;
import org.taumc.launcher.core.launch.RuntimeInstance;

import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        RuntimeInstance instance = RuntimeInstance.fromMMCPack(Paths.get("mmc-pack.json"));
        var accountService = new AccountService();
        accountService.loadFromDisk();
        if (accountService.getLoadedAccounts().isEmpty()) {
            var msAccount = new MicrosoftAccount();
            msAccount.login();
            accountService.getLoadedAccounts().add(msAccount);
            accountService.saveToDisk();
        }
        var account = accountService.getLoadedAccounts().getFirst();
        instance.setLaunchAccount(account);
        instance.launch();
    }
}
