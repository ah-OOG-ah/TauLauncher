package org.taumc.launcher.gui.screens.settings;

import org.taumc.launcher.core.jvm.JavaScanner;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.taumc.launcher.core.jvm.JavaScanner.JvmInstallation;

public class JvmSelectionDialog extends JDialog {
    private final DefaultListModel<JvmInstallation> listModel = new DefaultListModel<>();
    private final JList<JvmInstallation> jvmList = new JList<>(listModel);
    private final JButton okButton = new JButton("OK");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton browseButton = new JButton("Browse...");

    private JvmInstallation selectedJvm = null;

    public JvmSelectionDialog(Window parent, List<JvmInstallation> discoveredJvms) {
        super(parent, "Select JVM", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(10, 10));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        discoveredJvms.forEach(listModel::addElement);

        jvmList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jvmList.setVisibleRowCount(8);
        JScrollPane scrollPane = new JScrollPane(jvmList);

        // Top panel with label and Browse button
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new JLabel("Select a JVM installation:"), BorderLayout.WEST);
        topPanel.add(browseButton, BorderLayout.EAST);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        // Action listeners
        okButton.addActionListener(e -> {
            selectedJvm = jvmList.getSelectedValue();
            dispose();
        });

        cancelButton.addActionListener(e -> {
            selectedJvm = null;
            dispose();
        });

        browseButton.addActionListener(this::onBrowse);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }

    private void onBrowse(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Java Executable");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            Path path = selectedFile.toPath();

            if (!selectedFile.canExecute()) {
                JOptionPane.showMessageDialog(this,
                        "Selected file is not executable.",
                        "Invalid JVM", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String version = JavaScanner.detectJavaVersion(path);
            if (version == null) {
                JOptionPane.showMessageDialog(this,
                        "Could not determine Java version from the selected executable.",
                        "Invalid JVM", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String displayName = version;

            // Avoid duplicates
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).javaExecutable().equals(path)) {
                    jvmList.setSelectedIndex(i);
                    return;
                }
            }

            JvmInstallation jvm = new JvmInstallation(displayName, path);
            listModel.addElement(jvm);
            jvmList.setSelectedValue(jvm, true);
        }
    }

    public JvmInstallation getSelectedJvm() {
        return selectedJvm;
    }
}
