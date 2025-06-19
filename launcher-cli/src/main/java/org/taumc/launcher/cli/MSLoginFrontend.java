package org.taumc.launcher.cli;

import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import org.taumc.launcher.core.auth.microsoft.MicrosoftAccount;

public class MSLoginFrontend implements MicrosoftAccount.LoginFlowFrontend {
    @Override
    public void displayDeviceCode(StepMsaDeviceCode.MsaDeviceCode deviceCode) {
        System.out.println("Navigate to the following URL: " + deviceCode.getDirectVerificationUri());
    }
}
