package org.taumc.launcher.testclient;

import org.taumc.launcher.core.auth.offline.OfflineAccount;
import org.taumc.launcher.core.launch.RuntimeInstance;
import org.taumc.launcher.core.meta.curseforge.CurseForgeMetaRepository;
import org.taumc.launcher.core.meta.json.ComponentCoordinate;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;

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
        instance.getMetadataService().updateIndex();
        instance.addComponent(new ComponentCoordinate.Simple("com.curseforge.projects.1039252", "6707705"));
        instance.setLaunchAccount(new OfflineAccount("Dev"));
        instance.launch();
        instance.getCurrentProcess().destroyForcibly();
    }
}
