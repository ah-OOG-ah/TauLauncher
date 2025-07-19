package org.taumc.launcher.testclient;

import org.taumc.launcher.core.meta.curseforge.CurseForgeMetaRepository;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.core.reconciler.ComponentHolder;
import org.taumc.launcher.core.reconciler.Reconciler;
import org.taumc.launcher.core.reconciler.ReconciliationOptions;
import org.taumc.launcher.core.reconciler.intervention.ConsoleInterventionHandler;
import org.taumc.launcher.core.util.SetUtils;

public class TestReconciler {
    public static void main(String[] args) throws Exception {
        var metaService = new MetadataService();
        metaService.addRepository(HTTPMetaRepository.prism());
        metaService.addRepository(new CurseForgeMetaRepository());
        metaService.updateIndex();

        var newPack = metaService.getComponent(new ComponentCoordinate.Simple("com.curseforge.projects.1039252", "6707705")).join();
        var oldPack = metaService.getComponent(new ComponentCoordinate.Simple("com.curseforge.projects.1039252", "6634170")).join();

        var oldHolder = new ComponentHolder(metaService);
        oldHolder.addComponent(oldPack);

        var newHolder = new ComponentHolder(metaService);
        newHolder.addComponent(newPack);

        // Determine change
        Reconciler reconciler = new Reconciler(null, oldHolder.getComponents(), metaService, ProgressProvider.LOGGING, new ConsoleInterventionHandler());

        Reconciler newReconciler = new Reconciler(null, newHolder.getComponents(), metaService, ProgressProvider.LOGGING, new ConsoleInterventionHandler());
        try (var oldOutput = reconciler.runReconciliation(ReconciliationOptions.builder().build());
             var newOutput = newReconciler.runReconciliation(ReconciliationOptions.builder().build())) {
            var oldPaths = oldOutput.result().managedPaths().keySet();
            var newPaths = newOutput.result().managedPaths().keySet();

            SetUtils.formatGroupedDiff(oldPaths, newPaths).forEach(System.out::println);
        }
    }
}
