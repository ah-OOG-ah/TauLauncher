package org.taumc.launcher.core.reconciler.intervention;

import java.util.function.BooleanSupplier;

public record InterventionAction(String message, Runnable actionExecutor, BooleanSupplier isInterventionComplete) {
}
