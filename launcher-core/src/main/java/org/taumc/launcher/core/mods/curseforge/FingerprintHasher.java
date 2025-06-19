package org.taumc.launcher.core.mods.curseforge;

import org.apache.commons.codec.digest.MurmurHash2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FingerprintHasher {
    private static boolean isFiltered(byte b) {
        return  b == 9 || b == 10 || b == 13 || b == 32;
    }

    public static int computeHash(Path path) throws IOException {
        return computeHash(Files.readAllBytes(path));
    }

    public static int computeHash(byte[] data) {
        // Remove all "whitespace" chars by copying in-place
        int writer = 0, reader = 0;
        while (reader < data.length) {
            if (!isFiltered(data[reader])) {
                data[writer] = data[reader];
                writer++;
            }
            reader++;
        }
        return MurmurHash2.hash32(data, writer, 1);
    }
}
