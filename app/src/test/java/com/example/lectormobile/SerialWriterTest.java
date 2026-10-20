package com.example.lectormobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SerialWriter: single-threaded FIFO writer used to move SQLite position
 * saves off the main thread (optimization S3, v1.4.3).
 */
public class SerialWriterTest {

    @Test(timeout = 5000)
    public void writesRunOffTheCallerThread() throws Exception {
        SerialWriter writer = new SerialWriter();
        try {
            AtomicReference<Thread> ranOn = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            writer.write(() -> {
                ranOn.set(Thread.currentThread());
                done.countDown();
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertFalse(Thread.currentThread().equals(ranOn.get()));
            assertEquals("serial-writer", ranOn.get().getName());
        } finally {
            writer.shutdown();
        }
    }

    @Test(timeout = 5000)
    public void writesExecuteInFifoOrder() throws Exception {
        SerialWriter writer = new SerialWriter();
        try {
            List<Integer> order = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < 100; i++) {
                final int n = i;
                writer.write(() -> order.add(n));
            }
            assertTrue(writer.flush(3000));
            assertEquals(100, order.size());
            for (int i = 0; i < 100; i++) {
                assertEquals(Integer.valueOf(i), order.get(i));
            }
        } finally {
            writer.shutdown();
        }
    }

    @Test(timeout = 5000)
    public void flushReturnsTrueOnlyAfterPendingWritesFinished() throws Exception {
        SerialWriter writer = new SerialWriter();
        try {
            List<String> log = Collections.synchronizedList(new ArrayList<>());
            writer.write(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                log.add("slow-write");
            });

            assertTrue(writer.flush(3000));
            // by the time flush returns, the slow write is already recorded
            assertEquals(1, log.size());
            assertEquals("slow-write", log.get(0));
        } finally {
            writer.shutdown();
        }
    }

    @Test(timeout = 5000)
    public void flushReturnsFalseOnTimeoutWithoutDroppingTheQueue() throws Exception {
        SerialWriter writer = new SerialWriter();
        try {
            List<String> log = Collections.synchronizedList(new ArrayList<>());
            writer.write(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                log.add("slow");
            });

            assertFalse(writer.flush(100));       // gives up waiting
            assertTrue(writer.flush(3000));       // the queued write still runs
            assertEquals(1, log.size());
        } finally {
            writer.shutdown();
        }
    }

    @Test(timeout = 5000)
    public void aThrowingWriteDoesNotWedgeLaterWrites() throws Exception {
        SerialWriter writer = new SerialWriter();
        try {
            List<String> log = Collections.synchronizedList(new ArrayList<>());
            writer.write(() -> { throw new IllegalStateException("db gone"); });
            writer.write(() -> log.add("after-failure"));
            assertTrue(writer.flush(3000));
            assertEquals(1, log.size());
        } finally {
            writer.shutdown();
        }
    }
}
