package org.taumc.launcher.core.reconciler.intervention;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConsoleInterventionHandler implements UserInterventionHandler {
    private static final long USER_REMINDER_TIME = TimeUnit.SECONDS.toNanos(10);

    @Override
    public void awaitUserIntervention(List<InterventionAction> messages) {
        List<InterventionAction> remainingInterventions = new ArrayList<>(messages);

        long lastUserActionTime = 0;
        do {
            if ((System.nanoTime() - lastUserActionTime) >= USER_REMINDER_TIME) {
                System.out.println("Updating this instance requires " + remainingInterventions.size() + " manual interventions: ");
                for (var message : remainingInterventions) {
                    System.out.print(" - ");
                    System.out.println(message.message());
                }

                // Prevent the reminder from being shown again
                lastUserActionTime = System.nanoTime();
            }

            if (remainingInterventions.removeIf(m -> m.isInterventionComplete().getAsBoolean())) {
                lastUserActionTime = System.nanoTime();
            }

            if (remainingInterventions.isEmpty()) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while(true);
    }
}
