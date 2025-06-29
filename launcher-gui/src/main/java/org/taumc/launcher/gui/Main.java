package org.taumc.launcher.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.auth.AccountService;
import org.taumc.launcher.core.meta.fabric.OrnitheIntermediaryMetaRepository;
import org.taumc.launcher.core.meta.json.BuiltinMetaRepository;
import org.taumc.launcher.core.meta.json.MetaRepository;
import org.taumc.launcher.core.meta.json.PatchedPrismMetaRepository;
import org.taumc.launcher.gui.screens.home.HomeModel;
import org.taumc.launcher.gui.screens.home.HomeView;
import org.taumc.launcher.core.meta.json.MetadataService;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static final MetadataService METADATA = new MetadataService();
    public static final AccountService ACCOUNTS = new AccountService();

    public static boolean isProduction() {
        return Boolean.getBoolean("tau.launcher.production");
    }

    static void start(String[] args) throws Exception {
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            getDefaultRepositories().forEach(METADATA::addRepository);
            try {
                METADATA.updateIndex();
            } catch (Exception e) {
                LOGGER.error("Error updating metadata", e);
                JOptionPane.showMessageDialog(null, e.getMessage(), "Error updating metadata", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                ACCOUNTS.loadFromDisk();
                Files.createDirectories(UIPaths.INSTANCES_FOLDER);
            } catch (IOException e) {
                LOGGER.error("Error reading launcher data from disk", e);
                JOptionPane.showMessageDialog(null, e.getMessage(), "Error reading launcher data from disk", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new HomeView(new HomeModel()).setVisible(true);
        });
    }

    public static List<MetaRepository> getDefaultRepositories() {
        return List.of(new PatchedPrismMetaRepository(), new BuiltinMetaRepository(), new OrnitheIntermediaryMetaRepository());
    }
}
