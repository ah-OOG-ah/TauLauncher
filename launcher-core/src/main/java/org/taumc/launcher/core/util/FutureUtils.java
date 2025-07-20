package org.taumc.launcher.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

public class FutureUtils {
    public static <T> CompletableFuture<T> anySuccessful(Collection<? extends CompletableFuture<? extends T>> futures) {
        CompletableFuture<T> result = new CompletableFuture<>();
        AtomicInteger remaining = new AtomicInteger(futures.size());

        List<Throwable> fails = Collections.synchronizedList(new ArrayList<>());
        for (CompletableFuture<? extends T> future : futures) {
            future.whenComplete((value, throwable) -> {
                if (throwable == null) {
                    result.complete(value);
                } else {
                    fails.add(throwable);
                    if (remaining.decrementAndGet() == 0) {
                        var completionException = new CompletionException("All futures failed", throwable);
                        for (var e : fails.toArray(new Throwable[0])) {
                            if (e != throwable) {
                                completionException.addSuppressed(e);
                            }
                        }
                        result.completeExceptionally(completionException);
                    }
                }
            });
        }

        return result;
    }

    @SafeVarargs
    public static <T> CompletableFuture<T> anySuccessful(CompletableFuture<? extends T>... futures) {
        return anySuccessful(Arrays.asList(futures));
    }

    public static <T> CompletableFuture<List<T>> allOfWithResults(Collection<CompletableFuture<T>> futures) {
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        return allDone.thenApply(v ->
                futures.stream()
                        .map(CompletableFuture::join)
                        .toList()
        );
    }
}
