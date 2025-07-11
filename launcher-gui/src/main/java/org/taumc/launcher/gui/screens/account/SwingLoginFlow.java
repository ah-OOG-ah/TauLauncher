package org.taumc.launcher.gui.screens.account;

import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import org.taumc.launcher.core.auth.microsoft.MicrosoftAccount;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class SwingLoginFlow implements MicrosoftAccount.LoginFlowFrontend {
    @Override
    public void displayDeviceCode(StepMsaDeviceCode.MsaDeviceCode deviceCode, CompletableFuture<Void> onLoginCompletion) {
        SwingUtilities.invokeLater(() -> {
            JPanel panel = new JPanel(new BorderLayout(10, 10));

            // Instruction text
            String message = "<html>"
                    + "To sign in, open this link in your browser:<br>"
                    + "<b>" + deviceCode.getVerificationUri() + "</b><br>"
                    + "Then enter the code:<br>"
                    + "<h2>" + deviceCode.getUserCode() + "</h2>"
                    + "<br>Or click the link below to open directly:"
                    + "</html>";
            JTextPane messageLabel = new JTextPane();
            messageLabel.setContentType("text/html");
            messageLabel.setText(message);
            messageLabel.setEditable(false);
            messageLabel.setOpaque(false);
            messageLabel.setBorder(null);
            panel.add(messageLabel, BorderLayout.NORTH);

            // Link label
            JLabel linkLabel = new JLabel("<html><a href=''>" + deviceCode.getDirectVerificationUri() + "</a></html>");
            linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            linkLabel.setForeground(Color.BLUE);
            linkLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    try {
                        Desktop.getDesktop().browse(new URI(deviceCode.getDirectVerificationUri()));
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null, "Failed to open link: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            linkPanel.add(linkLabel);
            panel.add(linkPanel, BorderLayout.CENTER);

            Object[] options = { "Cancel" };

            // Show dialog
            var optionPane = new JOptionPane(
                    panel,
                    JOptionPane.PLAIN_MESSAGE,
                    JOptionPane.DEFAULT_OPTION,
                    null,
                    options,
                    options[0]
            );

            var dialog = optionPane.createDialog("Microsoft Account Login");
            dialog.setModal(true);
            onLoginCompletion.whenCompleteAsync((c, t) -> {
                dialog.dispose();
            }, SwingUtilities::invokeLater);
            dialog.setVisible(true);
        });
    }
}
