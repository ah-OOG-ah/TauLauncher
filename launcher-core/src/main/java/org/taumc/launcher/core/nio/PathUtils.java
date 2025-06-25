package org.taumc.launcher.core.nio;

import org.taumc.launcher.core.progress.ProgressProvider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Predicate;

public class PathUtils {
    public static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void copyTrackingProgress(Path source, Path target, ProgressProvider.Task task) throws IOException {
        long totalBytes = Files.size(source);
        long copiedBytes = 0L;
        int bufferSize = 64 * 1024; // 16 KB

        try (
                FileChannel inChannel = FileChannel.open(source, StandardOpenOption.READ);
                FileChannel outChannel = FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        ) {
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

            while (inChannel.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    copiedBytes += outChannel.write(buffer);
                }
                buffer.clear();

                task.setProgress(100.0f * copiedBytes / totalBytes);
            }
        }
    }

    public static String findNonexistentName(String baseName, Predicate<String> existenceCheck) {
        String name = baseName;
        int i = 0;
        while (existenceCheck.test(name)) {
            name = baseName + "(" + i++ + ")";
        }
        return name;
    }
}
