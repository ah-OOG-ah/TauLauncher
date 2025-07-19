package org.taumc.launcher.core.reconciler.exceptions;

import lombok.Getter;
import org.taumc.launcher.core.reconciler.intervention.InterventionAction;

import java.util.List;

public final class UserInterventionRequiredException extends RecoverableReconcilerException {
    @Getter
    private final List<InterventionAction> actions;

    public UserInterventionRequiredException(InterventionAction action) {
        this(List.of(action));
    }

    public UserInterventionRequiredException(List<InterventionAction> actions) {
        super("User must take " + actions.size() + " actions");
        if (actions.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.actions = actions;
    }
}
