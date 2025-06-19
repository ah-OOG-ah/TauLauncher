package org.taumc.launcher.gui.launch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

public class LogViewFrame extends JFrame {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogViewFrame.class);
    private final Style defaultStyle, warnStyle, errorStyle, systemStyle;
    private final JTextPane logPane;
    private final JScrollPane scrollPane;
    private final Process gameProcess;

    public LogViewFrame(String title, Process gameProcess) {
        this.setTitle("Logs for '" + title + "'");
        this.setLayout(new BorderLayout());
        this.gameProcess = gameProcess;

        this.logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        StyledDocument doc = logPane.getStyledDocument();

        // Define styles for dark mode

        defaultStyle = doc.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, Color.LIGHT_GRAY);  // light gray for normal text

        warnStyle = doc.addStyle("warn", null);
        StyleConstants.setForeground(warnStyle, new Color(255, 200, 0)); // golden yellow

        errorStyle = doc.addStyle("error", null);
        StyleConstants.setForeground(errorStyle, new Color(255, 100, 100)); // soft red

        systemStyle = doc.addStyle("system", null);
        StyleConstants.setForeground(systemStyle, new Color(180, 140, 255)); // or your chosen color

        this.scrollPane = new JScrollPane(logPane);
        this.add(scrollPane, BorderLayout.CENTER);

        this.setSize(800, 600);

        // Create popup menu
        JPopupMenu popup = new JPopupMenu();
        JMenuItem copy = new JMenuItem("Copy");
        copy.addActionListener(e -> logPane.copy());
        popup.add(copy);


        // Bottom panel with search bar, search button, and bottom button
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JTextField searchField = new JTextField();
        JButton searchButton = new JButton("Search");
        JButton bottomButton = new JButton("Bottom");

        // Right-side panel for the two buttons
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonPanel.add(searchButton);
        buttonPanel.add(bottomButton);

        AtomicInteger lastMatchPos = new AtomicInteger(0);

        searchButton.addActionListener(ev -> {
            String searchText = searchField.getText().toLowerCase();
            if (searchText.isEmpty()) return;

            try {
                String fullText = doc.getText(0, doc.getLength()).toLowerCase();

                int start = lastMatchPos.get();
                int matchIndex = fullText.indexOf(searchText, start);

                // Wrap around if not found
                if (matchIndex == -1 && start > 0) {
                    matchIndex = fullText.indexOf(searchText, 0);
                }

                if (matchIndex != -1) {
                    logPane.setCaretPosition(matchIndex);
                    logPane.moveCaretPosition(matchIndex + searchText.length());
                    logPane.requestFocusInWindow();
                    lastMatchPos.set(matchIndex + searchText.length());
                } else {
                    JOptionPane.showMessageDialog(this, "No match found", "Search", JOptionPane.INFORMATION_MESSAGE);
                }

            } catch (BadLocationException ex) {
                LOGGER.error("Unexpected exception", ex);
            }
        });

        bottomPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        bottomPanel.add(searchField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        this.add(bottomPanel, BorderLayout.SOUTH);

        bottomButton.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                logPane.setCaretPosition(logPane.getDocument().getLength());
            });
        });

        // Add mouse listener to show popup on right-click
        logPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        new LogReaderThread(gameProcess.getInputStream()).start();
        new LogReaderThread(gameProcess.getErrorStream()).start();

        gameProcess.onExit().thenRunAsync(() -> {
            appendLog("Game has exited with code " + gameProcess.exitValue() + ".", systemStyle);
        }, SwingUtilities::invokeLater);
    }

    public void appendLog(String line, Style style) {
        try {
            StyledDocument doc = logPane.getStyledDocument();
            boolean isAtBottom = scrollPane.getVerticalScrollBar().getValue() == scrollPane.getVerticalScrollBar().getMaximum();
            doc.insertString(doc.getLength(), line + "\n", style);
            if (isAtBottom) {
                logPane.setCaretPosition(doc.getLength()); // auto-scroll
            }
        } catch (BadLocationException e) {
            LOGGER.error("Unexpected location", e);
        }
    }

    private class LogReaderThread extends Thread {
        private final InputStream stream;

        LogReaderThread(InputStream stream) {
            this.stream = stream;
            this.setDaemon(true);
        }

        private Style determineStyle(String line) {
            if (line.contains("ERROR")) return errorStyle;
            if (line.contains("WARN")) return warnStyle;
            return defaultStyle;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    var style = determineStyle(line);
                    String finalLine = line;
                    SwingUtilities.invokeLater(() -> appendLog(finalLine, style));
                }
            } catch (IOException e) {
            }
        }
    }
}
