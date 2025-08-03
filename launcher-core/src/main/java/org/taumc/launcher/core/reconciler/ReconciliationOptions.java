package org.taumc.launcher.core.reconciler;

import lombok.Builder;

@Builder
public record ReconciliationOptions() {
    /**
     * Controls how existing files in the instance are updated.
     */
    public enum UpdateMode {
        /**
         * Files are updated only if they don't exist in the instance.
         */
        UPDATE_IF_MISSING,
        /**
         * Files are updated whenever they differ from what the component would apply.
         */
        UPDATE_IF_DIFFERENT
    }
}
