package org.taumc.launcher.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import org.taumc.launcher.core.auth.AccountService;
import org.taumc.launcher.core.meta.fabric.OrnitheIntermediaryMetaRepository;
import org.taumc.launcher.core.meta.json.HTTPMetaRepository;
import org.taumc.launcher.core.meta.json.MetaRepository;
import org.taumc.launcher.gui.screens.home.HomeModel;
import org.taumc.launcher.gui.screens.home.HomeView;
import org.taumc.launcher.core.meta.json.MetadataService;

import javax.swing.*;
import java.nio.file.Files;
import java.util.List;

public class Main {
    public static final MetadataService METADATA = new MetadataService();
    public static final AccountService ACCOUNTS = new AccountService();

    public static boolean isProduction() {
        return Boolean.getBoolean("tau.launcher.production");
    }

    public static void main(String[] args) throws Exception {
        getDefaultRepositories().forEach(METADATA::addRepository);
        METADATA.updateIndex();
        ACCOUNTS.loadFromDisk();
        FlatDarkLaf.setup();
        Files.createDirectories(UIPaths.INSTANCES_FOLDER);
        SwingUtilities.invokeLater(() -> {
            new HomeView(new HomeModel()).setVisible(true);
        });
    }

    public static List<MetaRepository> getDefaultRepositories() {
        return List.of(HTTPMetaRepository.prism(), new OrnitheIntermediaryMetaRepository());
    }
}
