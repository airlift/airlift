/*
 * Copyright Starburst Data, Inc. All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF STARBURST DATA.
 * The copyright notice above does not evidence any
 * actual or intended publication of such source code.
 *
 * Redistribution of this material is strictly prohibited.
 */
package io.airlift.stats;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TDigestThreadSafetyTest
{
    /**
     * Data Integrity & Race Conditions
     * * If TDigest is NOT thread-safe:
     * 1. threads will race to increment 'totalWeight', resulting in a lower final count.
     * 2. threads will race to read/write 'centroidCount', causing IndexOutOfBoundsException
     * or overwriting each other's data in the 'means' array.
     */
    @Test
    public void testConcurrentAdds()
            throws InterruptedException
    {
        final int numThreads = 10;
        final int operationsPerThread = 10_000;
        final TDigest tDigest = new TDigest();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start all at once
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Add value 1.0 with weight 1.0
                        tDigest.add(1.0, 1.0);
                    }
                }
                catch (Throwable t) {
                    error.set(t);
                }
                finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Blast off
        boolean finished = doneLatch.await(100, TimeUnit.SECONDS);

        if (!finished) {
            fail("Test timed out - possible deadlock in add()");
        }
        if (error.get() != null) {
            error.get().printStackTrace();
            fail("Exception during concurrent execution: " + error.get().getMessage());
        }

        // Assertion: If 10 threads add 10,000 items of weight 1, total weight MUST be 100,000.
        // Without 'synchronized' or locks, this assertion will fail (count will be ~99,000).
        assertEquals(numThreads * operationsPerThread, tDigest.getCount(), 0.001,
                "Total weight mismatch! Race condition detected.");

        // Sanity check: serialization shouldn't throw exceptions
        tDigest.serialize();
        executor.shutdownNow();
    }

    /**
     * Deadlock Prevention in mergeWith
     * * Scenario: Thread 1 merges B into A. Thread 2 merges A into B.
     * If the locks are not acquired in a globally consistent order (e.g., by identity hash),
     * this WILL deadlock:
     * Thread 1 holds A, waits for B.
     * Thread 2 holds B, waits for A.
     */
    @Test
    public void testDeadlockMergeWith()
            throws InterruptedException
    {
        final TDigest digestA = new TDigest();
        final TDigest digestB = new TDigest();

        // Pre-fill with some data so merges actually do work
        for (int i = 0; i < 100; i++) {
            digestA.add(i);
            digestB.add(i + 100);
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);

        // Task 1: Repeatedly merge B into A
        Future<?> f1 = executor.submit(() -> {
            try {
                latch.await();
                while (running.get()) {
                    digestA.mergeWith(digestB);
                    Thread.yield(); // Give the other thread a chance to lock
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        // Task 2: Repeatedly merge A into B
        Future<?> f2 = executor.submit(() -> {
            try {
                latch.await();
                while (running.get()) {
                    digestB.mergeWith(digestA);
                    Thread.yield();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        latch.countDown(); // Start hammering

        // Let them fight for 4 seconds
        Thread.sleep(4000);

        // If we reached here without hanging, we probably didn't deadlock.
        // Stop the threads.
        running.set(false);
        executor.shutdown();

        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);

        if (!terminated) {
            // Force kill if stuck
            executor.shutdownNow();
            fail("Deadlock detected! Threads failed to terminate after merge loop.");
        }

        assertTrue(f1.isDone() && !f1.isCancelled());
        assertTrue(f2.isDone() && !f2.isCancelled());
    }

    /**
     * Concurrent Read/Write (Rescale vs Add)
     * * Ensures that rescale (which modifies structure) doesn't crash a concurrent reader.
     */
    @Test
    public void testRescaleConcurrency()
            throws InterruptedException
    {
        final TDigest tDigest = new TDigest();
        for (int i = 0; i < 1000; i++) {
            tDigest.add(i); // seed data
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> error = new AtomicReference<>();

        // Writer Thread: Continuously rescales
        executor.submit(() -> {
            long landmark = 0;
            while (running.get()) {
                try {
                    tDigest.rescale(0.5, landmark, landmark + 100);
                    landmark += 100;
                    Thread.sleep(1);
                }
                catch (Throwable t) {
                    error.set(t);
                    running.set(false);
                }
            }
        });

        // Reader Thread: Continuously queries
        executor.submit(() -> {
            while (running.get()) {
                try {
                    tDigest.valueAt(0.5);
                    tDigest.getMin();
                    tDigest.getMax();
                }
                catch (Throwable t) {
                    error.set(t);
                    running.set(false);
                }
            }
        });

        Thread.sleep(1000);
        running.set(false);
        executor.shutdown();

        if (error.get() != null) {
            error.get().printStackTrace();
            fail("Concurrency error during rescale/read: " + error.get().getMessage());
        }
    }
}
