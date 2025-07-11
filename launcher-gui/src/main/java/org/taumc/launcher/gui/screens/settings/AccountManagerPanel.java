package org.taumc.launcher.gui.screens.settings;

import org.taumc.launcher.core.auth.Account;
import org.taumc.launcher.core.auth.microsoft.MicrosoftAccount;
import org.taumc.launcher.gui.Main;
import org.taumc.launcher.gui.SwingHelpers;
import org.taumc.launcher.gui.minecraft.SkinHelper;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AccountManagerPanel extends JPanel {
    private final DefaultListModel<Account> accountListModel = new DefaultListModel<>();
    private final JList<Account> accountList = new JList<>(accountListModel);
    private final JButton addButton = new JButton("Add");
    private final JButton removeButton = new JButton("Remove");
    private final Map<String, CompletableFuture<BufferedImage>> headCache = new HashMap<>();

    private CompletableFuture<BufferedImage> obtainHead(String skinUrl) {
        return headCache.computeIfAbsent(skinUrl, url -> CompletableFuture.supplyAsync(() -> {
            try {
                var skinImage = ImageIO.read(new URL(skinUrl));
                var head = SkinHelper.extractHead(skinImage);
                skinImage.flush();
                var scaledHead = SwingHelpers.scaleImagePixelPerfect(head, 4);
                head.flush();
                return scaledHead;
            } catch (IOException e) {
                return null;
            }
        }).whenCompleteAsync((c, t) -> accountList.repaint(), SwingUtilities::invokeLater));
    }

    public AccountManagerPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Add initial accounts
        Main.ACCOUNTS.getLoadedAccounts().forEach(accountListModel::addElement);

        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setFixedCellHeight(40);
        accountList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value instanceof Account account) {
                    setText(account.username());

                    var head = account.skinUrl().map(AccountManagerPanel.this::obtainHead).orElseGet(() -> CompletableFuture.completedFuture(null)).getNow(null);
                    if (head != null) {
                        setIcon(new ImageIcon(head));
                    } else {
                        setIcon(null);
                    }

                    setIconTextGap(10); // Space between icon and text
                }

                return this;
            }
        });
        accountList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (accountList.getSelectedValue() != null) {
                    Main.ACCOUNTS.setPreferredAccount(accountList.getSelectedValue());
                    try {
                        Main.ACCOUNTS.saveToDisk();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                removeButton.setEnabled(accountList.getSelectedValue() != null);
            }
        });
        JScrollPane scrollPane = new JScrollPane(accountList);
        scrollPane.setPreferredSize(new Dimension(200, 150));

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonsPanel.add(addButton);
        buttonsPanel.add(removeButton);

        add(scrollPane, BorderLayout.CENTER);
        add(buttonsPanel, BorderLayout.SOUTH);

        // Actions
        addButton.addActionListener(e -> onAddAccount());
        removeButton.addActionListener(e -> onRemoveAccount());
        accountList.setSelectedValue(Main.ACCOUNTS.getPreferredAccount(), true);
    }

    private void onAddAccount() {
        var microsoftAccount = new MicrosoftAccount();
        CompletableFuture.runAsync(microsoftAccount::login).thenRun(() -> SwingUtilities.invokeLater(() -> {
            if (microsoftAccount.isLoggedIn()) {
                Main.ACCOUNTS.getLoadedAccounts().add(microsoftAccount);
                try {
                    Main.ACCOUNTS.saveToDisk();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                accountListModel.addElement(microsoftAccount);
            }
        }));
    }

    private void onRemoveAccount() {
        int selectedIndex = accountList.getSelectedIndex();
        if (selectedIndex != -1) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to remove this account?",
                    "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                Main.ACCOUNTS.getLoadedAccounts().remove(selectedIndex);
                try {
                    Main.ACCOUNTS.saveToDisk();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                accountListModel.remove(selectedIndex);
            }
        }
    }
}
