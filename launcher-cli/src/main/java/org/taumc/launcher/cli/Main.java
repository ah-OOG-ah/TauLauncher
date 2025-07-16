package org.taumc.launcher.cli;

import org.taumc.launcher.core.auth.AccountService;
import org.taumc.launcher.core.auth.microsoft.MicrosoftAccount;
import org.taumc.launcher.core.launch.RuntimeInstance;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;
import org.taumc.launcher.core.meta.json.MMCPack;

import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        var mmcPack = MMCPack.read(Paths.get("mmc-pack.json"));
        RuntimeInstance instance = new RuntimeInstance();
        instance.addComponents(mmcPack.components());
        instance.getMetadataService().addRepository(HTTPMetaRepository.prism());
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
