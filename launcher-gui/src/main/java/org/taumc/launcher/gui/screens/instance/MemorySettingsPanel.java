package org.taumc.launcher.gui.screens.instance;

import com.sun.management.OperatingSystemMXBean;
import org.apache.commons.text.StringEscapeUtils;
import org.taumc.launcher.core.jvm.JavaScanner;
import org.taumc.launcher.core.launch.InstanceCfg;
import org.taumc.launcher.core.qsettings.Settings;
import org.taumc.launcher.gui.screens.settings.JvmSelectionDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.ArrayList;

public class MemorySettingsPanel extends JPanel {
    private static final int MEMORY_STEP_SIZE = 128;
    private static final int MEMORY_MINIMUM = 1;
    private static final int MEMORY_MAXIMUM = findSystemMemorySizeMB();

    private final JSpinner minMemorySpinner;
    private final JSpinner maxMemorySpinner;
    private final JTextArea jvmArgsArea;
    private final JTextArea jvmPath;
    private final Settings instanceCfg;

    private JavaScanner.JvmInstallation selectedJvmInstallation;

    public MemorySettingsPanel(Settings instanceCfg) {
        this.instanceCfg = instanceCfg;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel for memory spinners
        JPanel jvmChoicePanel = new JPanel();
        jvmChoicePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        jvmChoicePanel.setLayout(new GridLayout(2, 2, 10, 5));

        jvmChoicePanel.add(new JLabel("Java Version:"));
        jvmPath = new JTextArea();
        jvmPath.setEditable(false);
        jvmChoicePanel.add(jvmPath);
        jvmChoicePanel.add(new JLabel());
        var chooseButton = new JButton("Choose");
        chooseButton.addActionListener(e -> this.showJvmDialog());
        jvmChoicePanel.add(chooseButton);

        // Panel for memory spinners
        JPanel memoryPanel = new JPanel();
        memoryPanel.setLayout(new GridLayout(2, 2, 10, 5));

        minMemorySpinner = new JSpinner(new SpinnerNumberModel(512, MEMORY_MINIMUM, MEMORY_MAXIMUM, MEMORY_STEP_SIZE));
        maxMemorySpinner = new JSpinner(new SpinnerNumberModel(2048, MEMORY_MINIMUM, MEMORY_MAXIMUM, MEMORY_STEP_SIZE));

        memoryPanel.add(new JLabel("Minimum Memory (MB):"));
        memoryPanel.add(minMemorySpinner);
        memoryPanel.add(new JLabel("Maximum Memory (MB):"));
        memoryPanel.add(maxMemorySpinner);

        // Panel for JVM args
        JPanel jvmArgsPanel = new JPanel(new BorderLayout(5, 5));
        jvmArgsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        jvmArgsPanel.add(new JLabel("Additional JVM Arguments:"), BorderLayout.NORTH);

        jvmArgsArea = new JTextArea();
        jvmArgsArea.setLineWrap(true);
        jvmArgsArea.setWrapStyleWord(true);
        jvmArgsArea.setBorder(null);
        jvmArgsArea.setAlignmentY(Component.TOP_ALIGNMENT);

        JScrollPane scrollPane = new JScrollPane(jvmArgsArea);

        jvmArgsPanel.add(scrollPane, BorderLayout.CENTER);

        // Add all to main panel
        add(jvmChoicePanel);
        add(memoryPanel);
        add(jvmArgsPanel);

        var generalCategory = instanceCfg.getOrCreateNested("General");
        generalCategory.getValue("JvmArgs").ifPresent(v -> {
            String finalValue = v;
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                finalValue = StringEscapeUtils.unescapeJava(v.substring(1, v.length() - 1));
            }
            jvmArgsArea.setText(finalValue);
        });
        generalCategory.getValue("MinMemAlloc").flatMap(InstanceCfg::tryParseInt).ifPresent(minMemorySpinner::setValue);
        generalCategory.getValue("MaxMemAlloc").flatMap(InstanceCfg::tryParseInt).ifPresent(maxMemorySpinner::setValue);
        selectedJvmInstallation = generalCategory.getValue("JavaLocation").filter(p -> !p.isBlank()).map(p -> new JavaScanner.JvmInstallation(p, Paths.get(p))).orElse(null);
        updateJvmPath();

        minMemorySpinner.addChangeListener(ev -> {
            if (getMinMemory() > getMaxMemory()) {
                maxMemorySpinner.setValue(getMinMemory());
            }

            updateInstanceCfg();
        });

        maxMemorySpinner.addChangeListener(ev -> {
            updateInstanceCfg();
        });

        jvmArgsArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateInstanceCfg();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateInstanceCfg();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateInstanceCfg();
            }
        });
    }

    private static int findSystemMemorySizeMB() {
        OperatingSystemMXBean os = (OperatingSystemMXBean)
                ManagementFactory.getOperatingSystemMXBean();

        long totalPhysical = os.getTotalMemorySize();

        return (int)(totalPhysical / 1000000L);
    }

    private void updateInstanceCfg() {
        var general = instanceCfg.getOrCreateNested("General");
        general.setValue("MaxMemAlloc", String.valueOf(getMaxMemory()));
        general.setValue("MinMemAlloc", String.valueOf(getMinMemory()));
        general.setValue("JvmArgs", "\"" + StringEscapeUtils.escapeJava(getJvmArgs()) + "\"");
        general.setValue("OverrideJavaArgs", "true");
        if (selectedJvmInstallation != null) {
            general.setValue("JavaPath", selectedJvmInstallation.javaExecutable().toAbsolutePath().toString());
            general.setValue("OverrideJavaLocation", "true");
        } else {
            general.setValue("OverrideJavaLocation", "false");
        }
    }

    private void updateJvmPath() {
        if (selectedJvmInstallation != null) {
            jvmPath.setText(selectedJvmInstallation.javaExecutable().toString());
        } else {
            jvmPath.setText("<launcher provisioned>");
        }
    }

    private void showJvmDialog() {
        var discovered = new ArrayList<>(JavaScanner.discoverInstalledJvms());
        var autoselect = new JavaScanner.JvmInstallation("Launcher provisioned", null);
        discovered.addFirst(autoselect);
        var jvms = new JvmSelectionDialog(null, discovered);
        jvms.setVisible(true);

        selectedJvmInstallation = jvms.getSelectedJvm();
        if (autoselect.equals(selectedJvmInstallation)) {
            selectedJvmInstallation = null;
        }
        updateJvmPath();
        updateInstanceCfg();
    }

    // Getters for retrieving values
    public int getMinMemory() {
        return (int) minMemorySpinner.getValue();
    }

    public int getMaxMemory() {
        return (int) maxMemorySpinner.getValue();
    }

    public String getJvmArgs() {
        return jvmArgsArea.getText().replace('\n', ' ').trim();
    }
}
