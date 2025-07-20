package org.taumc.launcher.core.util;

import java.util.Optional;

public class EnumUtils {
    public static <T extends Enum<T>> Optional<T> valueOfOptional(Class<T> enumClass, String name) {
        try {
            return Optional.of(Enum.valueOf(enumClass, name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
