package com.example.lectormobile;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Single-threaded FIFO writer (optimization S3, v1.4.3).
 *
 * <p>Moves SQLite writes off the caller's thread while preserving the order
 * the writes were queued in: the reading-position save is debounced on the
 * main thread (it needs the WebView to compute the fraction) and the actual
 * database write runs here, on one background thread.
 *
 * <p>{@link #flush(long)} is the closing barrier: it returns {@code true}
 * only once every write queued before the call has finished, so
 * {@code onPause} can guarantee the freshest position is persisted before the
 * activity goes away. A throwing write never wedges later writes.
 */
public class SerialWriter {

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "serial-writer");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Queues a write; runs in FIFO order, off the caller's thread. A write
     * that throws is logged and skipped instead of killing the process (an
     * uncaught exception on any Android thread crashes the app) and never
     * blocks the writes queued behind it.
     */
    public void write(Runnable task) {
        if (task == null) return;
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                System.err.println("SerialWriter: write failed: " + e);
                e.printStackTrace();
            }
        });
    }

    /**
     * Blocks until every write queued before this call finished, at most
     * {@code timeoutMs} milliseconds.
     *
     * @return {@code true} when the queue drained in time, {@code false} on
     *         timeout or internal failure (queued writes are never dropped)
     */
    public boolean flush(long timeoutMs) {
        Future<?> barrier = executor.submit(() -> { });
        try {
            barrier.get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }

    /** Stops accepting writes; queued ones still run (best effort). */
    public void shutdown() {
        executor.shutdown();
    }
}
