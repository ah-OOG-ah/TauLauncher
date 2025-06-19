package org.taumc.launcher.gui.screens.home;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.nio.PathUtils;
import org.taumc.launcher.core.qsettings.Settings;
import org.taumc.launcher.gui.UIPaths;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class HomeModel {
    private final List<String> instanceFolders = new ArrayList<>();
    private String selectedInstance;
    private static final Logger LOGGER = LoggerFactory.getLogger(HomeModel.class);

    public HomeModel() {
        updateInstanceFolders();
    }

    public void updateInstanceFolders() {
        this.instanceFolders.clear();
        try (Stream<Path> stream = Files.find(UIPaths.INSTANCES_FOLDER, 1, (p, a) -> !p.equals(UIPaths.INSTANCES_FOLDER) && a.isDirectory())) {
            stream.map(p -> p.getFileName().toString()).sorted().forEach(this.instanceFolders::add);
        } catch (IOException e) {
            LOGGER.error("Unexpected IO error", e);
        }
        if (!this.instanceFolders.contains(this.selectedInstance)) {
            this.selectedInstance = null;
        }
    }

    public void createInstance(String name) throws IOException {
        Path instancePath = getInstancePath(name);
        Files.createDirectory(instancePath);
        Path mmcPackJson = instancePath.resolve("mmc-pack.json");
        Files.writeString(mmcPackJson, "{\"components\":[],\"formatVersion\": 1}");
        updateInstanceFolders();
    }

    public void renameInstance(String oldName, String newName) throws IOException {
        Path instancePath = getInstancePath(oldName);

        if (!Files.exists(instancePath)) {
            return;
        }

        Files.move(instancePath, UIPaths.INSTANCES_FOLDER.resolve(newName));

        if (oldName.equals(selectedInstance)) {
            selectedInstance = newName;
        }

        updateInstanceFolders();
    }

    public void deleteInstance(String name) throws IOException {
        Path instancePath = getInstancePath(name);

        if (!Files.exists(instancePath)) {
            return;
        }

        PathUtils.deleteRecursively(instancePath);

        if (name.equals(selectedInstance)) {
            selectedInstance = null;
        }

        updateInstanceFolders();
    }

    public Path getInstancePath(String name) {
        return UIPaths.INSTANCES_FOLDER.resolve(name);
    }

    public Settings getSettings(String name) {
        Path instancePath = getInstancePath(name);

        try (var is = Files.newInputStream(instancePath.resolve("instance.cfg"))) {
            return Settings.read(is);
        } catch (IOException e) {
            return new Settings();
        }
    }

    public List<String> getInstanceFolders() {
        return Collections.unmodifiableList(this.instanceFolders);
    }

    public String getSelectedInstance() {
        return selectedInstance;
    }

    public void setSelectedInstance(String selectedInstance) {
        this.selectedInstance = selectedInstance;
    }
}
