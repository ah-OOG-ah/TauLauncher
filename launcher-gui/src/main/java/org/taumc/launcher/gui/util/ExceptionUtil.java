package org.taumc.launcher.gui.util;

import java.util.LinkedHashSet;
import java.util.Set;

public class ExceptionUtil {
    public static String getCompactErrorMessage(Throwable throwable) {
        Set<String> messages = new LinkedHashSet<>();
        getErrorMessages(messages, throwable);
        var sb = new StringBuilder();
        for (var m : messages) {
            sb.append(" - ");
            sb.append(m);
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void getErrorMessages(Set<String> set, Throwable throwable) {
        set.add(throwable.toString());
        if (throwable.getCause() != null) {
            getErrorMessages(set, throwable.getCause());
        }
        for (var t : throwable.getSuppressed()) {
            getErrorMessages(set, t);
        }
    }
}
