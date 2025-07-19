package org.taumc.launcher.core.reconciler;

public sealed abstract class RecoverableReconcilerException extends Exception permits MissingDependenciesException {
    protected RecoverableReconcilerException(String message) {
        super(message);
    }
}
