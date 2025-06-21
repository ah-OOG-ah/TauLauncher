package org.taumc.launcher.gui.screens.curseforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.taumc.launcher.core.mods.curseforge.CurseForgeInstanceCreator;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ManualDownloadDialog extends JFrame implements CurseForgeInstanceCreator.ManualDownloadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManualDownloadDialog.class);

    private static final Map<Integer, String> CLASS_TO_URL_SLUG = Map.of(
            12, "texture-packs",
            6945, "data-packs",
            6552, "shaders",
            6, "mc-mods"
    );

    private final List<Download> downloads = new ArrayList<>();

    private CompletableFuture<Void> completableFuture = new CompletableFuture<>();

    public ManualDownloadDialog() {

    }

    private List<String> getUrls() {
        return downloads.stream().map(d -> {
            var classSlug = CLASS_TO_URL_SLUG.get(d.mod().classId());
            if (classSlug == null) {
                throw new IllegalArgumentException("Unknown class ID " + d.mod().classId() + " from mod " + d.mod().slug());
            }
            return "https://legacy.curseforge.com/minecraft/" + classSlug + "/" + d.mod().slug() + "/download/" + d.file().id();
        }).toList();
    }

    private void initUi() {
        setLayout(new BorderLayout(10, 10));

        JLabel explanation = new JLabel("<html>Some mods cannot be downloaded automatically due to CurseForge API restrictions.<br>" +
                "Please download them manually from the following URLs:</html>");
        explanation.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(explanation, BorderLayout.NORTH);

        JEditorPane urlPane = new JEditorPane();
        urlPane.setContentType("text/html");
        urlPane.setEditable(false);
        urlPane.setOpaque(false);

        StringBuilder html = new StringBuilder("<html><ul>");
        List<String> modUrls = getUrls();
        for (String url : modUrls) {
            html.append("<li><a href=\"").append(url).append("\">").append(url).append("</a></li>");
        }
        html.append("</ul></html>");

        urlPane.setText(html.toString());
        urlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openUrl(e.getURL().toString());
            }
        });

        JScrollPane scrollPane = new JScrollPane(urlPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton openAllButton = new JButton("Open All Links");
        JButton cancelButton = new JButton("Cancel");

        openAllButton.addActionListener(e -> modUrls.forEach(ManualDownloadDialog::openUrl));
        cancelButton.addActionListener(e -> {
            completableFuture.completeExceptionally(new IOException("Manual downloading was aborted"));
            dispose();
        });
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        buttonPanel.add(openAllButton);
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);

        setSize(600, 400);
    }

    private static void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "Failed to open URL: " + url + "\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void trackFileForManualDownload(Download download) {
        this.downloads.add(download);
    }

    private static Path getDownloadsFolder() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();

        // This covers most common OSes
        if (os.contains("win")) {
            return Paths.get(userHome, "Downloads");
        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Downloads");
        } else if (os.contains("nux") || os.contains("nix")) {
            return Paths.get(userHome, "Downloads");
        }

        // Fallback
        return Paths.get(userHome);
    }

    @Override
    public CompletableFuture<Void> downloadManualFiles() {
        if (this.downloads.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        SwingUtilities.invokeLater(() -> {
            try {
                this.initUi();
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
                JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
            this.setVisible(true);
        });
        var thread = new DownloadMonitoringThread(completableFuture, downloads);
        thread.setName("CF download monitor");
        thread.start();
        completableFuture.whenCompleteAsync((c, t) -> {
            LOGGER.info("All downloads of manual files complete");
            this.dispose();
        }, SwingUtilities::invokeLater);
        return completableFuture;
    }

    private static class DownloadMonitoringThread extends Thread {
        private final CompletableFuture<Void> downloadCompletionFuture;
        private final List<Download> downloads;
        private final Set<String> fileNameCache;

        private DownloadMonitoringThread(CompletableFuture<Void> downloadCompletionFuture, List<Download> downloads) {
            this.downloadCompletionFuture = downloadCompletionFuture;
            this.downloads = new ArrayList<>(downloads);
            this.fileNameCache = downloads.stream().map(d -> d.destination().getFileName().toString()).collect(Collectors.toSet());
        }

        /**
         * {@return false if this download file should be deleted}
         */
        private boolean processDownload(Path filePath) {
            Path filename = filePath.getFileName();

            // Skip common partial download files
            if (isTemporaryDownload(filename.toString())) {
                return true;
            }

            String nameStr = filename.toString();
            var download = downloads.stream().filter(d -> d.destination().getFileName().toString().equals(nameStr)).findFirst();

            if (download.isEmpty()) {
                return true;
            }
            var value = download.get();

            try {
                if (processDownload(filePath, value)) {
                    downloads.remove(value);
                    // Will already be deleted
                    return true;
                } else {
                    return false;
                }
            } catch (IOException e) {
                downloadCompletionFuture.completeExceptionally(e);
                return true;
            }
        }

        private void monitorDownloads() throws Exception {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                Path downloadsDir = getDownloadsFolder();
                downloadsDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                List<Path> deletions = new ArrayList<>();
                try (Stream<Path> stream = Files.list(downloadsDir)) {
                    stream.filter(Files::isRegularFile).filter(p -> fileNameCache.contains(p.getFileName().toString())).forEach(p -> {
                        if (!processDownload(p)) {
                            deletions.add(p);
                        }
                    });
                }
                for (var deletion : deletions) {
                    Files.delete(deletion);
                }
                while (!downloads.isEmpty()) {
                    WatchKey key = watchService.take(); // wait for event

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind != StandardWatchEventKinds.ENTRY_CREATE && kind != StandardWatchEventKinds.ENTRY_MODIFY) continue;

                        Path filename = (Path) event.context();
                        Path filePath = downloadsDir.resolve(filename);

                        processDownload(filePath);
                    }

                    if (!key.reset()) {
                        downloadCompletionFuture.completeExceptionally(new IOException("Watch key reset"));
                    }
                }
                downloadCompletionFuture.complete(null);
            }
        }

        private static boolean isTemporaryDownload(String filename) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".crdownload") || lower.endsWith(".part") || lower.endsWith(".tmp");
        }

        private static boolean processDownload(Path file, Download download) throws IOException {
            long expectedSize = download.file().fileLength();
            if (Files.size(file) != expectedSize) {
                return false;
            }

            LOGGER.info("Assuming file {} is done being downloaded", file.getFileName().toString());

            Files.copy(file, download.destination(), StandardCopyOption.REPLACE_EXISTING);
            long newSize = Files.size(download.destination());
            if (newSize != expectedSize) {
                Files.delete(download.destination());
                throw new IOException("Size on disk of " + file.getFileName().toString() + " is not expected, should be " + expectedSize+ " but was " + newSize);
            }
            Files.delete(file);
            return true;
        }

        @Override
        public void run() {
            try {
                monitorDownloads();
            } catch (Throwable e) {
                downloadCompletionFuture.completeExceptionally(e);
            }
        }
    }
}
