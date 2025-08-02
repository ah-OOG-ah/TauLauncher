package org.taumc.launcher.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.auth.AccountService;
import org.taumc.launcher.core.meta.curseforge.CurseForgeMetaRepository;
import org.taumc.launcher.core.meta.fabric.OrnitheIntermediaryMetaRepository;
import org.taumc.launcher.core.meta.prism.BuiltinMetaRepository;
import org.taumc.launcher.core.meta.json.MetaRepository;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;
import org.taumc.launcher.core.meta.prism.PatchedPrismMetaRepository;
import org.taumc.launcher.gui.screens.home.HomeModel;
import org.taumc.launcher.gui.screens.home.HomeView;
import org.taumc.launcher.core.meta.json.MetadataService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static final MetadataService METADATA = new MetadataService();
    public static final AccountService ACCOUNTS = new AccountService();

    public static boolean isProduction() {
        return Boolean.getBoolean("tau.launcher.production");
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    static void start(String[] args) throws Exception {
        if (SystemUtils.IS_OS_MAC) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "TauLauncher");
            System.setProperty("apple.awt.application.name", "TauLauncher");
            System.setProperty("apple.awt.application.appearance", "dark");
        }

        FlatDarkLaf.setGlobalExtraDefaults(Map.of("@background", "#282a2b"));
        FlatDarkLaf.setup();

        Color base = UIManager.getColor("Table.background");
        int boost = 10;
        Color subtleAlt = new Color(
                clamp(base.getRed() - boost),
                clamp(base.getGreen() - boost),
                clamp(base.getBlue() - boost)
        );
        UIManager.put("Table.alternateRowColor", subtleAlt);

        SwingUtilities.invokeLater(() -> {
            try {
                getDefaultRepositories().forEach(METADATA::addRepository);
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
        return List.of(HTTPMetaRepository.prism(), new BuiltinMetaRepository(), new OrnitheIntermediaryMetaRepository(), new CurseForgeMetaRepository());
    }
}
