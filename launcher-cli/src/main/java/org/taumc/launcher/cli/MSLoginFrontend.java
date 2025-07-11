package org.taumc.launcher.cli;

import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import org.taumc.launcher.core.auth.microsoft.MicrosoftAccount;

import java.util.concurrent.CompletableFuture;

public class MSLoginFrontend implements MicrosoftAccount.LoginFlowFrontend {
    @Override
    public void displayDeviceCode(StepMsaDeviceCode.MsaDeviceCode deviceCode, CompletableFuture<Void> future) {
        System.out.println("Navigate to the following URL: " + deviceCode.getDirectVerificationUri());
    }
}
