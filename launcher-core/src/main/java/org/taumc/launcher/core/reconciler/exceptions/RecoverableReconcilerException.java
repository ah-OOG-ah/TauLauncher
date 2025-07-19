package org.taumc.launcher.core.reconciler.exceptions;

public sealed abstract class RecoverableReconcilerException extends Exception permits MissingDependenciesException, UserInterventionRequiredException {
    protected RecoverableReconcilerException(String message) {
        super(message);
    }
}
