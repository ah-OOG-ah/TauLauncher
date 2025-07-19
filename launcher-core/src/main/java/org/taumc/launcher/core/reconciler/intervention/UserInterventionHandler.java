package org.taumc.launcher.core.reconciler.intervention;

import java.util.List;

public interface UserInterventionHandler {
    void awaitUserIntervention(List<InterventionAction> messages);
}
