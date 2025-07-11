package org.taumc.launcher.core.auth;

import com.google.gson.FormattingStyle;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.auth.microsoft.MicrosoftAccount;
import org.taumc.launcher.core.auth.offline.OfflineAccount;
import org.taumc.launcher.core.gson.RuntimeTypeAdapterFactory;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AccountService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountService.class);
    private static final Path DEFAULT_ACCOUNT_STORE = LauncherPaths.getLauncherCache().resolve("accounts.json");
    private static final GsonBuilder BUILDER = new GsonBuilder();

    private static class AccountStoreData {
        private final List<Account> loadedAccounts = new ArrayList<>();
        private UUID preferredAccount = null;
    }

    private AccountStoreData currentData = new AccountStoreData();

    private final Path accountStore;

    static {
        BUILDER.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(Account.class)
                .registerSubtype(MicrosoftAccount.class, "microsoft")
                .registerSubtype(OfflineAccount.class, "offline")
        );
        BUILDER.registerTypeAdapter(StepFullJavaSession.FullJavaSession.class, new MicrosoftAccount.SessionAdapter());
        BUILDER.serializeNulls();
        BUILDER.setFormattingStyle(FormattingStyle.PRETTY);
    }

    public AccountService() {
        this(DEFAULT_ACCOUNT_STORE);
    }

    public AccountService(Path accountStore) {
        this.accountStore = accountStore;
    }

    public void loadFromDisk() throws IOException {
        var gson = BUILDER.create();
        try {
            this.currentData = gson.fromJson(new JsonReader(new InputStreamReader(Files.newInputStream(this.accountStore))), new TypeToken<AccountStoreData>() {});
        } catch (Exception e) {
            LOGGER.error("Error loading account data", e);
        }
    }

    public List<Account> getLoadedAccounts() {
        return this.currentData.loadedAccounts;
    }

    public Account getPreferredAccount() {
        var preferred = this.currentData.loadedAccounts.stream().filter(a -> a.uuid().equals(this.currentData.preferredAccount)).findFirst();
        if (preferred.isPresent()) {
            return preferred.get();
        } else if (!this.currentData.loadedAccounts.isEmpty()) {
            return this.currentData.loadedAccounts.getFirst();
        } else {
            return null;
        }
    }

    public void setPreferredAccount(Account account) {
        this.currentData.preferredAccount = account.uuid();
    }

    public void saveToDisk() throws IOException {
        var gson = BUILDER.create();
        var jsonStr = gson.toJson(this.currentData, new TypeToken<AccountStoreData>() {}.getType());
        Files.writeString(this.accountStore, jsonStr);
    }
}
