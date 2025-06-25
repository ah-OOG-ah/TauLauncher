package org.taumc.launcher.gui.launch;

import org.taumc.launcher.core.progress.ProgressProvider;
import org.taumc.launcher.gui.SwingHelpers;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProgressDialog extends JDialog implements ProgressProvider {
    private final JPanel taskPanel;
    private final Map<String, JProgressBar> taskMap = new ConcurrentHashMap<>();

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

    public synchronized void startTask(String taskId, String message) {
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
                setVisible(true);

                if (SwingHelpers.isAnyWindowFocused()) {
                    setAlwaysOnTop(true);
                    toFront();
                    requestFocus();
                    setAlwaysOnTop(false);
                }
            }
        });
    }

    public synchronized void endTask(String taskId) {
        SwingUtilities.invokeLater(() -> {
            JProgressBar bar = taskMap.remove(taskId);
            if (bar != null) {
                taskPanel.remove(bar);
                taskPanel.revalidate();
                taskPanel.repaint();
            }

            SwingUtilities.invokeLater(() -> {
                if (taskMap.isEmpty()) {
                    setVisible(false); // Closes modal dialog
                }
            });
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
        startTask(taskName, taskName);
        return new Task() {
            @Override
            public void setMessage(String message) {
                updateTaskMessage(taskName, message);
            }

            @Override
            public void setProgress(float progress) {
                SwingUtilities.invokeLater(() -> {
                    JProgressBar bar = taskMap.get(taskName);
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
                endTask(taskName);
            }
        };
    }
}
