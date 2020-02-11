/*
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
package com.facebook.airlift.concurrent;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestConcurrentScheduledExecutor
{
    private ConcurrentScheduledExecutor scheduler;

    @BeforeClass
    public void setUp()
    {
        scheduler = new ConcurrentScheduledExecutor(2, 1, "test-");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
            throws Exception
    {
        scheduler.shutdownNow();
        assertTrue(scheduler.awaitTermination(10, TimeUnit.SECONDS));
        scheduler = null;
    }

    @Test
    public void testSubmit()
            throws Exception
    {
        AtomicInteger counter = new AtomicInteger();
        Future future = scheduler.submit(() -> counter.incrementAndGet());
        assertEquals(future.get(), 1);
        assertEquals(counter.get(), 1);
    }

    @Test
    public void testSchedule()
            throws Exception
    {
        AtomicInteger counter = new AtomicInteger();
        ScheduledFuture scheduledFuture = scheduler.schedule(
                () -> counter.incrementAndGet(),
                1,
                TimeUnit.SECONDS);
        assertEquals(scheduledFuture.get(), 1);
        assertEquals(counter.get(), 1);
    }

    @Test
    public void testAwaitTerminating()
            throws Exception
    {
        ConcurrentScheduledExecutor scheduler = new ConcurrentScheduledExecutor(2, 1, "test-");
        AtomicInteger counter = new AtomicInteger();

        try {
            // schedule two tasks to run with < 100 ms delay
            for (int i = 0; i < 4; ++i) {
                scheduler.schedule(() -> counter.incrementAndGet(),
                        ThreadLocalRandom.current().nextInt(1000),
                        TimeUnit.MILLISECONDS);
            }
        }
        finally {
            scheduler.shutdown();
            // wait for 10 seconds, tasks should have completed
            assertTrue(scheduler.awaitTermination(10, TimeUnit.SECONDS));
            assertTrue(scheduler.isTerminated());
            scheduler = null;
            assertEquals(counter.get(), 4);
        }
    }
}
