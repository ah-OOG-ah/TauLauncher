package org.taumc.launcher.core.auth.microsoft;

import com.google.gson.*;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import org.taumc.launcher.core.auth.Account;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MicrosoftAccount implements Account {
    private StepFullJavaSession.FullJavaSession currentSession;
    private transient final HttpClient httpClient = MinecraftAuth.createHttpClient();

    private static final MicrosoftAccount.LoginFlowFrontend FRONTEND = ServiceLoader.load(LoginFlowFrontend.class, MicrosoftAccount.class.getClassLoader()).findFirst().orElseThrow(() -> new IllegalStateException("A login frontend must be provided to use MicrosoftAccount"));

    public void login() {
        var future = new CompletableFuture<Void>();
        try {
            this.currentSession = MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.getFromInput(httpClient, new StepMsaDeviceCode.MsaDeviceCodeCallback(msaDeviceCode -> {
                FRONTEND.displayDeviceCode(msaDeviceCode, future);
            }));
            future.complete(null);
        } catch (Exception e) {
            future.completeExceptionally(e);
            e.printStackTrace();
        }
    }

    public boolean isLoggedIn() {
        return this.currentSession != null && !this.currentSession.isExpired();
    }

    @Override
    public void refresh(ProgressProvider progressProvider) throws Exception {
        if (this.currentSession == null) {
            throw new Exception("Not logged in");
        }
        if (this.currentSession.getMcProfile().isExpired()) {
            try (var task = progressProvider.addTask("Refreshing account...")) {
                this.currentSession = MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.refresh(httpClient, this.currentSession);
            } catch (Exception e) {
                throw new Exception("Error refreshing Microsoft account", e);
            }
        }
    }

    @Override
    public String username() {
        return this.currentSession.getMcProfile().getName();
    }

    @Override
    public UUID uuid() {
        return this.currentSession.getMcProfile().getId();
    }

    @Override
    public String accessToken() {
        return this.currentSession.getMcProfile().getMcToken().getAccessToken();
    }

    @Override
    public Optional<String> skinUrl() {
        return Optional.ofNullable(this.currentSession.getMcProfile().getSkinUrl());
    }

    public static class SessionAdapter implements JsonSerializer<StepFullJavaSession.FullJavaSession>, JsonDeserializer<StepFullJavaSession.FullJavaSession> {
        @Override
        public StepFullJavaSession.FullJavaSession deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.fromJson((JsonObject)json);
        }

        @Override
        public JsonElement serialize(StepFullJavaSession.FullJavaSession src, Type typeOfSrc, JsonSerializationContext context) {
            return MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.toJson(src);
        }
    }

    public interface LoginFlowFrontend {
        void displayDeviceCode(StepMsaDeviceCode.MsaDeviceCode deviceCode, CompletableFuture<Void> onLoginCompletion);
    }

    @Override
    public String type() {
        return "msa";
    }

    @Override
    public String toString() {
        if (this.currentSession != null) {
            return this.currentSession.getMcProfile().getName();
        } else {
            return super.toString();
        }
    }
}
