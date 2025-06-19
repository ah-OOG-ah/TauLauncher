package org.taumc.launcher.core.auth;

import com.google.gson.FormattingStyle;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import org.taumc.launcher.core.auth.microsoft.MicrosoftAccount;
import org.taumc.launcher.core.auth.offline.OfflineAccount;
import org.taumc.launcher.core.gson.RuntimeTypeAdapterFactory;
import org.taumc.launcher.core.storage.LauncherPaths;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AccountService {
    private static final Path DEFAULT_ACCOUNT_STORE = LauncherPaths.getLauncherCache().resolve("accounts.json");
    private static final GsonBuilder BUILDER = new GsonBuilder();

    private final List<Account> loadedAccounts = new ArrayList<>();
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
        this.loadedAccounts.clear();
        try {
            this.loadedAccounts.addAll(gson.fromJson(new JsonReader(new InputStreamReader(Files.newInputStream(this.accountStore))), new TypeToken<List<Account>>() {}));
        } catch (NoSuchFileException ignored) {
        }
    }

    public List<Account> getLoadedAccounts() {
        return this.loadedAccounts;
    }

    public void saveToDisk() throws IOException {
        var gson = BUILDER.create();
        var jsonStr = gson.toJson(this.loadedAccounts, new TypeToken<List<Account>>() {}.getType());
        Files.writeString(this.accountStore, jsonStr);
    }
}
