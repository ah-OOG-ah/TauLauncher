package org.taumc.launcher.testclient;

import org.taumc.launcher.core.auth.offline.OfflineAccount;
import org.taumc.launcher.core.launch.RuntimeInstance;
import org.taumc.launcher.core.meta.curseforge.CurseForgeMetaRepository;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;
import org.taumc.launcher.core.meta.prism.PrismZipExportComponent;
import org.taumc.launcher.core.reconciler.tree.ComponentTreeNode;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestLauncher {
    public static void main(String[] args) throws Exception {
        Path instanceDir = Paths.get("/tmp/my-test-instance/minecraft");
        Files.createDirectories(instanceDir);
        RuntimeInstance instance = new RuntimeInstance() {
            @Override
            protected void configureProcessBuilder(ProcessBuilder builder) {
                builder.inheritIO();
            }
        };
        instance.setInstancePath(instanceDir);
        instance.getMetadataService().addRepository(HTTPMetaRepository.prism());
        instance.getMetadataService().addRepository(new CurseForgeMetaRepository());
        instance.getComponents().addChild(new ComponentTreeNode(new PrismZipExportComponent("test", "1.0", URI.create("https://downloads.gtnewhorizons.com/Multi_mc_downloads/GT_New_Horizons_2.7.4_Java_17-21.zip"))));
        //instance.addComponent(new ComponentCoordinate.Simple("com.curseforge.projects.1091252", "6270870"));
        instance.setLaunchAccount(new OfflineAccount("Dev"));
        instance.launch();
        instance.getCurrentProcess().destroyForcibly();
    }
}
