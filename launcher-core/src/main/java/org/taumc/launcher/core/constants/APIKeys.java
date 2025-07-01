package org.taumc.launcher.core.constants;

/**
 * All API keys in this class should be changed if forking the launcher.
 */
public class APIKeys {
    public static final String CURSEFORGE = findKey("CURSEFORGE_CORE_API_KEY", "taulauncher.curseforge.key", "$2a$10$i6iAcI6XXzv53fKRksUk2u1Tz8zYPHOvD0xgPxnDB/ZKKdy73xFSi");

    private static String findKey(String envVar, String sysProp, String defaultKey) {
        String key = System.getenv(envVar);
        if (key != null && !key.isBlank()) {
            return key;
        } else {
            return System.getProperty(sysProp, defaultKey);
        }
    }
}
