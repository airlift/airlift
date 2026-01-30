/*
 * Copyright 2013 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.stats;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeDistributionThreadSafetyTest
{
    // A Ticker that we can manipulate to force the TimeDistribution
    // to think 100ms has passed every single time.
    static class FastForwardTicker
            extends Ticker
    {
        private final AtomicLong time = new AtomicLong(0);

        @Override
        public long read()
        {
            // Every time read() is called, we advance time by 101ms.
            // This forces 'ticker.read() - lastMerge >= MERGE_THRESHOLD_NANOS'
            // to be TRUE on every single call.
            return time.addAndGet(MILLISECONDS.toNanos(101));
        }
    }

    /**
     * * This test forces a merge on EVERY read.
     */
    @Test
    public void testConcurrentReadWriteException()
            throws InterruptedException
    {
        // Use our manipulated ticker
        Ticker fastTicker = new FastForwardTicker();
        TimeDistribution dist = new TimeDistribution(fastTicker);

        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        // 1. Fill it with some initial data so the TDigest has something to sort
        for (int i = 0; i < 1000; i++) {
            dist.add(i);
        }

        // 2. The Reader Thread
        // Calls getP99(). Because of FastForwardTicker, this triggers
        // internal 'merged.merge()' logic while simultaneously trying to
        // read the return value.
        Runnable reader = () -> {
            try {
                while (running.get()) {
                    // This retrieves 'merged' (unsafe) then calculates
                    // valueAt (unsafe) while 'merged' is being mutated by the Ticker update.
                    dist.getP99();
                }
            }
            catch (Exception e) {
                exceptions.add(e);
            }
        };

        // 3. The Writer Thread
        // Continually adds data to ensure the internal buffers actuality
        // have things to merge, causing array resizing/sorting.
        Runnable writer = () -> {
            try {
                while (running.get()) {
                    dist.add(ThreadLocalRandom.current().nextLong(100));
                }
            }
            catch (Exception e) {
                // this should not fail as add is actually thread safe
                // but even if it fails we want to ignore, as this is a dumb data `chaos generator`
                // we don't want this code to affect the main objective of this test
            }
        };

        // Start 2 readers and 2 writers
        executor.submit(reader);
        executor.submit(reader);
        executor.submit(writer);
        executor.submit(writer);

        // Run for 2 seconds
        Thread.sleep(2000);
        running.set(false);
        executor.shutdown();
        executor.awaitTermination(1, SECONDS);

        if (!exceptions.isEmpty()) {
            System.out.println("Captured Exception type: " + exceptions.get(0).getClass().getName());
        }

        // This should now fail reliably
        exceptions.forEach(Throwable::printStackTrace);
        assertTrue(exceptions.isEmpty(),
                "The class is not thread safe! Concurrent modification exceptions occurred: " + exceptions);
    }
}
