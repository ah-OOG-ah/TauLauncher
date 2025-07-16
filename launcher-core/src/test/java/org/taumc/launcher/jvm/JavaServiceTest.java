package org.taumc.launcher.jvm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.taumc.launcher.core.jvm.JavaService;
import org.taumc.launcher.core.meta.prism.Component;
import org.taumc.launcher.core.meta.prism.HTTPMetaRepository;
import org.taumc.launcher.core.meta.json.MetadataService;
import org.taumc.launcher.core.progress.ProgressProvider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaServiceTest {
    private static final MetadataService META = new MetadataService();

    @BeforeAll
    static void setupService() throws Exception {
        META.addRepository(HTTPMetaRepository.prism());
        META.updateIndex();
    }

    @ParameterizedTest
    @ValueSource(ints = {21})
    void testJvmDownload(int version) throws Exception {
        var component = (Component)META.getComponent("net.adoptium.java", "java" + version).join();
        String jvmBinaryPath = JavaService.INSTANCE.provisionJVMBinary(component.uid(), component.version(), component.runtimes(), ProgressProvider.LOGGING);

        ProcessBuilder pb = new ProcessBuilder(jvmBinaryPath, "-version");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        int exitCode = process.waitFor();

        assertEquals(0, exitCode);
        String versionStr = version > 8 ? String.valueOf(version) : ("1." + version);
        assertTrue(lines.stream().anyMatch(s -> s.startsWith("openjdk version \"" + versionStr + ".")), "Unexpected Java version found");
    }
}
