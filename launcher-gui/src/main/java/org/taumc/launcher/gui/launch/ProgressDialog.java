package org.taumc.launcher.gui.launch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.gui.SwingHelpers;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ProgressDialog extends JDialog implements ProgressProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressDialog.class);

    private final JPanel taskPanel;
    private final Map<String, JProgressBar> taskMap = new ConcurrentHashMap<>();

    private boolean pendingVisibility;
    private Timer visibilityUpdateTimer;

    public ProgressDialog(Frame owner) {
        super(owner, "Working...", ModalityType.DOCUMENT_MODAL);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        setResizable(false);

        taskPanel = new JPanel();
        taskPanel.setLayout(new BoxLayout(taskPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(taskPanel);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);
        setSize(400, 200);
        setLocationRelativeTo(owner);
    }

    private void changeVisibilityNow() {
        setVisible(pendingVisibility);
        visibilityUpdateTimer = null;
    }

    private void changeVisibility(boolean visibility) {
        pendingVisibility = visibility;
        // Only update real visibility once every 500 ms
        if (visibilityUpdateTimer == null) {
            visibilityUpdateTimer = new Timer(500, e -> changeVisibilityNow());
            visibilityUpdateTimer.setRepeats(false);
            visibilityUpdateTimer.start();
        }
    }

    private void startTask(String taskId, String message) {
        SwingUtilities.invokeLater(() -> {
            if (taskMap.containsKey(taskId)) return;

            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            bar.setStringPainted(true);
            bar.setString(message);

            taskMap.put(taskId, bar);
            taskPanel.removeAll();
            taskMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> taskPanel.add(e.getValue()));
            taskPanel.revalidate();
            taskPanel.repaint();

            if (!isVisible()) {
                LOGGER.debug("Making progress dialog visible due to task: {}", taskId);
                changeVisibility(true);

                if (SwingHelpers.isAnyWindowFocused()) {
                    setAlwaysOnTop(true);
                    toFront();
                    requestFocus();
                    setAlwaysOnTop(false);
                }
            }
        });
    }

    private void endTask(String taskId) {
        SwingUtilities.invokeLater(() -> {
            JProgressBar bar = taskMap.remove(taskId);
            if (bar != null) {
                taskPanel.remove(bar);
                taskPanel.revalidate();
                taskPanel.repaint();
            }

            if (taskMap.isEmpty()) {
                LOGGER.debug("Making progress dialog invisible");
                changeVisibility(false);
            }
        });
    }

    public synchronized void updateTaskMessage(String taskId, String newMessage) {
        SwingUtilities.invokeLater(() -> {
            JProgressBar bar = taskMap.get(taskId);
            if (bar != null) {
                bar.setString(newMessage);
            }
        });
    }

    @Override
    public Task addTask(String taskName) {
        var taskId = UUID.randomUUID().toString();
        startTask(taskId, taskName);
        return new Task() {
            @Override
            public void setMessage(String message) {
                updateTaskMessage(taskId, message);
            }

            @Override
            public void setProgress(float progress) {
                SwingUtilities.invokeLater(() -> {
                    JProgressBar bar = taskMap.get(taskId);
                    if (bar != null) {
                        bar.setIndeterminate(false);
                        bar.setMinimum(0);
                        bar.setMaximum(100);
                        bar.setValue(Math.round(progress * 100));
                    }
                });
            }

            @Override
            public void close() {
                endTask(taskId);
            }
        };
    }
}
