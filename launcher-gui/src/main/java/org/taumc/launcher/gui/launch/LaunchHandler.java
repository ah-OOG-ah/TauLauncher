package org.taumc.launcher.gui.launch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.auth.Account;
import org.taumc.launcher.core.launch.InstanceCfg;
import org.taumc.launcher.core.meta.json.MMCPack;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.meta.prism.PatchesFolderMetaRepository;
import org.taumc.launcher.core.reconciler.tree.ComponentTreeNode;
import org.taumc.launcher.gui.Main;
import org.taumc.launcher.gui.UIPaths;
import org.taumc.launcher.core.launch.RuntimeInstance;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LaunchHandler {
    private static final Executor LAUNCH_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Logger LOGGER = LoggerFactory.getLogger(LaunchHandler.class);

    private final String instance;

    private RuntimeInstance gameInstance;
    private CompletableFuture<Void> launchCompletionFuture = CompletableFuture.completedFuture(null);
    private Account account;

    private final ProgressDialog progressDialog;

    public LaunchHandler(String instance, Frame owner, Account account) {
        this.instance = instance;
        this.progressDialog = new ProgressDialog(owner);
        this.account = account;
    }

    public static Path computeMinecraftFolder(Path instancePath) {
        var validPaths = List.of(instancePath.resolve("minecraft"), instancePath.resolve(".minecraft"));
        return validPaths.stream().filter(Files::isDirectory).findFirst().orElse(validPaths.getFirst());
    }

    public static void configureMetadataService(MetadataService metadataService, Path instancePath) throws IOException {
        var patchesFolder = instancePath.resolve("patches");
        if (Files.isDirectory(patchesFolder)) {
            metadataService.addRepository(new PatchesFolderMetaRepository(patchesFolder));
        }
        Main.getDefaultRepositories().forEach(metadataService::addRepository);
        metadataService.updateIndex();
    }

    public CompletableFuture<Void> doLaunch() {
        var instancePath = UIPaths.INSTANCES_FOLDER.resolve(instance);
        try {
            this.gameInstance = new RuntimeInstance();

            Path minecraftFolder = computeMinecraftFolder(instancePath);
            // Set instance folder
            Files.createDirectories(minecraftFolder);
            this.gameInstance.setInstancePath(minecraftFolder);

            var mmcPack = MMCPack.read(instancePath.resolve("mmc-pack.json"));

            // Configure metadata sources for this instance
            configureMetadataService(this.gameInstance.getMetadataService(), instancePath);

            // Inject selected components
            mmcPack.components().stream()
                    .map(this.gameInstance.getMetadataService()::getComponent)
                    .toList()
                    .forEach(future -> this.gameInstance.getComponents().addChild(new ComponentTreeNode(future.join())));

            // Apply configuration from instance.cfg
            InstanceCfg.configureInstanceWithCfg(this.gameInstance, instancePath.resolve("instance.cfg"));

            // Configure selected account
            this.gameInstance.setLaunchAccount(account);
            this.gameInstance.setProgressProvider(progressDialog);

            // Start the launch process
            this.launchCompletionFuture = CompletableFuture.runAsync(() -> {
                try {
                    this.gameInstance.launch();
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException)e;
                    } else {
                        throw new RuntimeException(e);
                    }
                }
                var process = this.gameInstance.getCurrentProcess();
                if (process != null) {
                    SwingUtilities.invokeLater(() -> {
                        var logView = LogViewFrame.forInstance(this.instance);
                        logView.clearLog();

                        logView.appendSystemMessage("Classpath:");
                        for (var path : this.gameInstance.getLaunchClasspath()) {
                            logView.appendSystemMessage("    " + path.toAbsolutePath().toString());
                        }
                        logView.appendSystemMessage("");

                        logView.attachProcess(process);
                    });
                }
            }, LAUNCH_EXECUTOR).whenComplete((p, t) -> {
                try {
                    Main.ACCOUNTS.saveToDisk();
                } catch (IOException e) {
                    LOGGER.error("Error persisting accounts", e);
                }
                if (t != null) {
                    LOGGER.error("Error launching game", t);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this.launchCompletionFuture;
    }

    public boolean isRunning() {
        var instance = this.gameInstance;
        var launchCompletionFuture = this.launchCompletionFuture;
        if (!launchCompletionFuture.isDone()) {
            return true;
        }
        return instance != null && instance.getCurrentProcess() != null && instance.getCurrentProcess().isAlive();
    }

    public CompletableFuture<Process> exitFuture() {
        if (this.gameInstance == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.gameInstance.getCurrentProcess().onExit();
    }

    public CompletableFuture<Process> terminate() {
        if (this.gameInstance == null) {
            return CompletableFuture.completedFuture(null);
        }
        this.launchCompletionFuture.join();
        var process = this.gameInstance.getCurrentProcess();
        this.gameInstance = null;
        return process.destroyForcibly().onExit();
    }

}
