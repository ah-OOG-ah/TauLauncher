package org.taumc.launcher.core.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public class StreamUtils {
    public static <T> Predicate<T> distinct(Function<? super T, ?> key) {
        var objects = ConcurrentHashMap.newKeySet();
        return t -> objects.add(key.apply(t));
    }
}
