package org.taumc.launcher.core.reconciler;

import lombok.Builder;

@Builder
public record ReconciliationOptions(boolean dryRun) {
}
