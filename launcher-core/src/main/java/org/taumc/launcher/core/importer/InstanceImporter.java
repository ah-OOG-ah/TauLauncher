package org.taumc.launcher.core.importer;

import org.taumc.launcher.core.mods.curseforge.CurseForgeAPI;
import org.taumc.launcher.core.mods.curseforge.CurseForgeInstanceCreator;
import org.taumc.launcher.core.nio.PathUtils;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class InstanceImporter {
    @FunctionalInterface
    private interface ImportHandler {
        void doImport(Path rootPath, Path targetPath, ProgressProvider progressProvider) throws IOException;
    }

    private static final Map<String, ImportHandler> IMPORT_KEYS = Map.of(
            "mmc-pack.json", InstanceImporter::importNativeInstance,
            "manifest.json", InstanceImporter::importCfInstance
    );

    private static Path findBaseFolder(Path rootPath) {
        Queue<Path> queue = new ArrayDeque<>();
        queue.add(rootPath);

        while (!queue.isEmpty()) {
            Path current = queue.poll();

            if (Files.isDirectory(current)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
                    for (Path entry : stream) {
                        queue.add(entry);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to read directory: " + current);
                    e.printStackTrace();
                }
            }

            Path fileName = current.getFileName();
            if (fileName != null && IMPORT_KEYS.containsKey(fileName.toString())) {
                return current.getParent();
            }
        }

        return null;
    }

    public static void importInstance(Path rootPath, Path targetPath, ProgressProvider progressProvider) throws IOException {
        rootPath = findBaseFolder(rootPath);
        if (rootPath == null) {
            throw new IOException("Don't understand how to import this instance");
        }

        Path finalRootPath = rootPath;
        var importHandler = IMPORT_KEYS.entrySet().stream()
                .filter(e -> Files.exists(finalRootPath.resolve(e.getKey())))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElseThrow(() -> new IOException("Instance does not contain one of the following files: " + String.join(", ", IMPORT_KEYS.keySet())));
        Files.createDirectories(targetPath);
        try (var task = progressProvider.addTask("Importing instance...")) {
            importHandler.doImport(rootPath, targetPath, progressProvider);
        }
    }

    private static void importCfInstance(Path rootPath, Path targetPath, ProgressProvider progressProvider) throws IOException {
        CurseForgeInstanceCreator creator = new CurseForgeInstanceCreator(CurseForgeAPI.INSTANCE);
        creator.createInstance(targetPath, rootPath, progressProvider);
    }

    private static void importNativeInstance(Path rootPath, Path targetPath, ProgressProvider progressProvider) throws IOException {
        // Copy everything into the target path
        long expectedBytes;
        try (Stream<Path> stream = Files.find(rootPath, Integer.MAX_VALUE, (p, a) -> !a.isDirectory())) {
            expectedBytes = stream.mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        }
        try (var task = progressProvider.addTask("Extracting instance files")) {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                long extractedBytes = 0;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetDir = targetPath.resolve(rootPath.relativize(dir).toString()).normalize();
                    if (!targetDir.startsWith(targetPath)) {
                        throw new IOException("Malformed zip path " + dir);
                    }
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relPath = rootPath.relativize(file).toString();
                    Path targetFile = targetPath.resolve(relPath).normalize();
                    if (!targetFile.startsWith(targetPath)) {
                        throw new IOException("Malformed zip path " + file);
                    }
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    extractedBytes += Files.size(file);
                    float progress = (float)extractedBytes / expectedBytes;
                    task.setProgress(Math.min(1, progress));
                    return FileVisitResult.CONTINUE;
                }
            });
        }

    }
}
