package org.taumc.launcher.core.progress;

public class Counter implements AutoCloseable {
    private final long total;
    private final ProgressProvider.Task task;
    private long count;

    public Counter(long total, ProgressProvider.Task task) {
        this.total = total;
        this.task = task;
    }

    public void increment() {
        increment(1);
    }

    public void increment(long amount) {
        count += amount;
        task.setProgress((float)((double)count / total));
    }

    @Override
    public void close() {
        task.close();
    }
}
