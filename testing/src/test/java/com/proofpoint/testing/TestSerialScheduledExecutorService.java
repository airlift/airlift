/*
 * Copyright 2012 Mark Kent
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
package com.proofpoint.testing;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Longs;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestSerialScheduledExecutorService
{
    private static final String INNER = "inner";
    private static final String OUTER = "outer";
    private SerialScheduledExecutorService executorService;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        executorService = new SerialScheduledExecutorService();
    }

    @Test
    public void testRunOnce()
            throws Exception
    {
        Counter counter = new Counter();
        executorService.execute(counter);

        assertEquals(counter.getCount(), 1);
    }

    @Test
    public void testThrownExceptionsAreSwallowedForRunOnceRunnable()
            throws Exception
    {
        executorService.execute(new Runnable()
        {
            @Override
            public void run()
            {
                throw new RuntimeException("deliberate");
            }
        });
    }

    @Test
    public void testSubmitRunnable()
            throws Exception
    {
        Counter counter = new Counter();
        Future<Integer> future = executorService.submit(counter, 10);

        assertEquals(counter.getCount(), 1);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals((int) future.get(), 10);
    }

    @Test
    public void testThrownExceptionsArePushedIntoFutureForSubmittedRunnable()
            throws Exception
    {
        Future<Integer> future = executorService.submit(new Runnable()
        {
            @Override
            public void run()
            {
                throw new RuntimeException("deliberate");
            }
        }, 10);

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        try {
            future.get();
        }
        catch (Exception expected) {
            assertEquals(expected.getMessage(), "java.lang.RuntimeException: deliberate");
            return;
        }

        fail("Should have received exception");
    }

    @Test
    public void testSubmitCallable()
            throws Exception
    {
        CallableCounter counter = new CallableCounter();
        Future<Integer> future = executorService.submit(counter);

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals((int) future.get(), 1);
    }

    @Test
    public void testThrownExceptionsArePushedIntoFutureForSubmittedCallable()
            throws Exception
    {
        Future<Integer> future = executorService.submit(new Callable<Integer>()
        {
            @Override
            public Integer call()
                    throws Exception
            {
                throw new Exception("deliberate");
            }
        });

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        try {
            future.get();
        }
        catch (Exception expected) {
            assertEquals(expected.getMessage(), "java.lang.Exception: deliberate");
            return;
        }

        fail("Should have received exception");
    }

    @Test
    public void testAdvanceNothingScheduled()
    {
        Ticker ticker = executorService.getTicker();
        long initialTick = ticker.read();

        executorService.elapseTime(10, TimeUnit.NANOSECONDS);
        assertEquals(ticker.read() - initialTick, 10);

        executorService.elapseTime(10, TimeUnit.NANOSECONDS);
        assertEquals(ticker.read() - initialTick, 20);

        executorService.elapseTimeNanosecondBefore(1, TimeUnit.MILLISECONDS);
        assertEquals(ticker.read() - initialTick, 20 + 999_999);
    }

    @Test
    public void testScheduleRunnable()
            throws Exception
    {
        Ticker ticker = executorService.getTicker();
        long initialTick = ticker.read();
        Counter counter = new TickedCounter(ticker, 10);
        Future<?> future = executorService.schedule(counter, 10, TimeUnit.NANOSECONDS);

        executorService.elapseTime(9, TimeUnit.NANOSECONDS);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);
        assertEquals(ticker.read() - initialTick, 9);

        executorService.elapseTime(1, TimeUnit.NANOSECONDS);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 1);
        assertEquals(ticker.read() - initialTick, 10);
    }

    @Test
    public void testThrownExceptionsArePushedIntoFutureForScheduledRunnable()
            throws Exception
    {
        Future<?> future = executorService.schedule(new Runnable()
        {
            @Override
            public void run()
            {
                throw new RuntimeException("deliberate");
            }
        }, 10, TimeUnit.MINUTES);

        executorService.elapseTime(10, TimeUnit.MINUTES);

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        boolean caught = false;
        try {
            future.get();
        }
        catch (Exception expected) {
            assertEquals(expected.getMessage(), "java.lang.RuntimeException: deliberate");
            caught = true;
        }

        assertTrue(caught, "Should have received exception");
    }

    @Test
    public void testScheduledRunnableWithZeroDelayCompletesImmediately()
            throws Exception
    {
        Ticker ticker = executorService.getTicker();
        long initialTick = ticker.read();
        Counter counter = new TickedCounter(ticker, 0);
        Future<?> future = executorService.schedule(counter, 0, TimeUnit.MINUTES);

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 1);
        assertEquals(ticker.read() - initialTick, 0);
    }

    @Test
    public void testCancelScheduledRunnable()
            throws Exception
    {
        Ticker ticker = executorService.getTicker();
        long initialTick = ticker.read();
        Counter counter = new TickedCounter(ticker);
        Future<?> future = executorService.schedule(counter, 10, TimeUnit.NANOSECONDS);

        executorService.elapseTime(9, TimeUnit.NANOSECONDS);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);
        assertEquals(ticker.read() - initialTick, 9);

        future.cancel(true);
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());

        executorService.elapseTime(1, TimeUnit.NANOSECONDS);
        assertEquals(counter.getCount(), 0);
        assertEquals(ticker.read() - initialTick, 10);
    }

    @Test
    public void testScheduleCallable()
            throws Exception
    {
        CallableCounter counter = new CallableCounter();
        Future<Integer> future = executorService.schedule(counter, 10, TimeUnit.NANOSECONDS);

        executorService.elapseTime(9, TimeUnit.NANOSECONDS);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        executorService.elapseTime(1, TimeUnit.NANOSECONDS);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 1);
        assertEquals((int) future.get(), 1);
    }

    @Test
    public void testThrownExceptionsArePushedIntoFutureForScheduledCallable()
            throws Exception
    {
        Future<Integer> future = executorService.schedule(new Callable<Integer>()
        {
            @Override
            public Integer call()
                    throws Exception
            {
                throw new Exception("deliberate");
            }
        }, 10, TimeUnit.MINUTES);

        executorService.elapseTime(10, TimeUnit.MINUTES);

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        boolean caught = false;
        try {
            future.get();
        }
        catch (Exception expected) {
            assertEquals(expected.getMessage(), "java.lang.Exception: deliberate");
            caught = true;
        }

        assertTrue(caught, "Should have received exception");
    }

    @Test
    public void testScheduledCallableWithZeroDelayCompletesImmediately()
            throws Exception
    {
        CallableCounter counter = new CallableCounter();
        Future<Integer> future = executorService.schedule(counter, 0, TimeUnit.MINUTES);

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 1);
    }

    @Test(expectedExceptions = CancellationException.class)
    public void testCancelScheduledCallable()
            throws Exception
    {
        CallableCounter counter = new CallableCounter();
        Future<Integer> future = executorService.schedule(counter, 10, TimeUnit.MINUTES);

        executorService.elapseTime(9, TimeUnit.MINUTES);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        future.cancel(true);
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());

        executorService.elapseTime(1, TimeUnit.MINUTES);
        assertEquals(counter.getCount(), 0);

        // Should throw
        future.get();
    }

    @Test
    public void testRepeatingRunnable()
            throws Exception
    {
        Ticker ticker = executorService.getTicker();
        long initialTick = ticker.read();
        Counter counter = new TickedCounter(ticker, 10, 15, 20);
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(counter, 10, 5, TimeUnit.NANOSECONDS);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        // After 9 nanoseconds, we shouldn't have run yet, and should have 1 nanosecond left
        executorService.elapseTime(9, TimeUnit.NANOSECONDS);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(future.getDelay(TimeUnit.NANOSECONDS), 1);
        assertEquals(counter.getCount(), 0);
        assertEquals(ticker.read() - initialTick, 9);

        // After 1 more nanosecond, we should have run once, and should have 5 nanoseconds remaining
        executorService.elapseTime(1, TimeUnit.NANOSECONDS);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(future.getDelay(TimeUnit.NANOSECONDS), 5);
        assertEquals(counter.getCount(), 1);
        assertEquals(ticker.read() - initialTick, 10);

        // After another 10 nanoseconds, we should have run twice more
        executorService.elapseTime(10, TimeUnit.NANOSECONDS);
        assertEquals(counter.getCount(), 3);
        assertEquals(ticker.read() - initialTick, 20);

    }

    @Test
    public void testRepeatingRunnableThatThrowsDoesNotRunAgain()
            throws Exception
    {
        FailingCounter counter = new FailingCounter(1);
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(counter, 10, 5, TimeUnit.MINUTES);

        executorService.elapseTime(10, TimeUnit.MINUTES);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 1);

        // The runnable will throw on the second attempt
        executorService.elapseTime(5, TimeUnit.MINUTES);
        assertEquals(counter.getCount(), 2);
        assertTrue(future.isDone());
        boolean caught = false;
        try {
            future.get();
        }
        catch (Exception expected) {
            assertEquals(expected.getMessage(), "java.lang.RuntimeException: deliberate");
            caught = true;
        }

        assertTrue(caught, "Should have received exception");

        // The runnable should not execute again
        executorService.elapseTime(20, TimeUnit.MINUTES);
        assertEquals(counter.getCount(), 2);
    }

    @Test
    public void testRepeatingRunnableThatThrowsDoesNotRunAgainWhenElapseContainsMultipleInvocations()
            throws Exception
    {
        FailingCounter counter = new FailingCounter(1);
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(counter, 10, 5, TimeUnit.MINUTES);

        executorService.elapseTime(10, TimeUnit.MINUTES);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 1);

        // The runnable will throw on the second attempt (out of three)
        executorService.elapseTime(10, TimeUnit.MINUTES);
        assertEquals(counter.getCount(), 2);
        assertTrue(future.isDone());
        boolean caught = false;
        try {
            future.get();
        }
        catch (Exception expected) {
            assertEquals(expected.getMessage(), "java.lang.RuntimeException: deliberate");
            caught = true;
        }

        assertTrue(caught, "Should have received exception");

        // The runnable should not execute again
        executorService.elapseTime(20, TimeUnit.MINUTES);
        assertEquals(counter.getCount(), 2);
    }

    @Test
    public void testRepeatingRunnableWithZeroDelayExecutesImmediately()
            throws Exception
    {
        Ticker ticker = executorService.getTicker();
        long initialTick = ticker.read();
        Counter counter = new TickedCounter(ticker, 0, 5, 10);
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(counter, 0, 5, TimeUnit.NANOSECONDS);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 1);
        assertEquals(future.getDelay(TimeUnit.NANOSECONDS), 5);

        // After another 10 nanoseconds, we should have run twice more
        executorService.elapseTime(10, TimeUnit.NANOSECONDS);
        assertEquals(counter.getCount(), 3);
        assertEquals(ticker.read() - initialTick, 10);
    }

    @Test
    public void testCancelRepeatingRunnableBeforeFirstRun()
            throws Exception
    {
        Ticker ticker = executorService.getTicker();
        long initialTick = ticker.read();
        Counter counter = new TickedCounter(ticker);
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(counter, 10, 5, TimeUnit.NANOSECONDS);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        executorService.elapseTime(9, TimeUnit.NANOSECONDS);

        future.cancel(true);

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEquals(counter.getCount(), 0);
        assertEquals(ticker.read() - initialTick, 9);

        executorService.elapseTime(1, TimeUnit.NANOSECONDS);
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEquals(counter.getCount(), 0);
        assertEquals(ticker.read() - initialTick, 10);
    }

    @Test
    public void testCancelRepeatingRunnableAfterFirstRun()
            throws Exception
    {
        Ticker ticker = executorService.getTicker();
        long initialTick = ticker.read();
        Counter counter = new TickedCounter(ticker, 10);
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(counter, 10, 5, TimeUnit.NANOSECONDS);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        executorService.elapseTime(10, TimeUnit.NANOSECONDS);

        future.cancel(true);

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEquals(counter.getCount(), 1);
        assertEquals(ticker.read() - initialTick, 10);

        executorService.elapseTime(5, TimeUnit.NANOSECONDS);
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEquals(counter.getCount(), 1);
        assertEquals(ticker.read() - initialTick, 15);
    }

    @Test
    public void testMultipleRepeatingRunnables()
            throws Exception
    {
        Ticker ticker = executorService.getTicker();
        long initialTick = ticker.read();
        Counter countEveryMinute = new TickedCounter(ticker, 1, 2, 3, 4, 5, 6, 7);
        Counter countEveryTwoMinutes = new TickedCounter(ticker, 2, 4, 6, 8);
        ScheduledFuture<?> futureEveryNano = executorService.scheduleAtFixedRate(countEveryMinute, 1, 1, TimeUnit.NANOSECONDS);
        executorService.scheduleAtFixedRate(countEveryTwoMinutes, 2, 2, TimeUnit.NANOSECONDS);

        executorService.elapseTime(7, TimeUnit.NANOSECONDS);

        assertEquals(countEveryMinute.getCount(), 7);
        assertEquals(countEveryTwoMinutes.getCount(), 3);
        assertEquals(ticker.read() - initialTick, 7);

        futureEveryNano.cancel(true);

        executorService.elapseTime(1, TimeUnit.NANOSECONDS);
        assertEquals(countEveryMinute.getCount(), 7);
        assertEquals(countEveryTwoMinutes.getCount(), 4);
        assertEquals(ticker.read() - initialTick, 8);
    }

    @Test
    public void testScheduleAtFixedRateWithLongerDelayFromWithinATask()
    {
        List<String> collector = new LinkedList<String>();
        long outerTaskDelay = TimeUnit.MINUTES.toMillis(10);
        long innerTaskDelay = TimeUnit.MINUTES.toMillis(20);
        long repeat = TimeUnit.DAYS.toMillis(10);

        executorService.scheduleAtFixedRate(
                createRunnableWithNestedScheduleAtFixedRate(collector, innerTaskDelay, repeat),
                outerTaskDelay,
                repeat,
                TimeUnit.MILLISECONDS);

        checkNestedScheduleWithLongerInnerTask(collector, outerTaskDelay, innerTaskDelay);
    }

    @Test
    public void testScheduleAtFixedRateWithShorterDelayFromWithinATask()
    {
        List<String> collector = new LinkedList<String>();
        long outerTaskDelay = TimeUnit.MINUTES.toMillis(10);
        long innerTaskDelay = TimeUnit.MINUTES.toMillis(20);
        long repeat = TimeUnit.DAYS.toMillis(10);

        executorService.scheduleAtFixedRate(
                createRunnableWithNestedScheduleAtFixedRate(collector, innerTaskDelay, repeat),
                outerTaskDelay,
                repeat,
                TimeUnit.MILLISECONDS);

        checkNestedScheduleWithShorterInnerTask(collector, outerTaskDelay, innerTaskDelay);
    }

    @Test
    public void testScheduleWithFixedDelayWithLongerDelayFromWithinATask()
    {
        List<String> collector = new LinkedList<String>();
        long outerTaskDelay = TimeUnit.MINUTES.toMillis(10);
        long innerTaskDelay = TimeUnit.MINUTES.toMillis(20);
        long repeat = TimeUnit.DAYS.toMillis(10);

        executorService.scheduleWithFixedDelay(
                createRunnableWithNestedScheduleWithFixedDelay(collector, innerTaskDelay, repeat),
                outerTaskDelay,
                repeat,
                TimeUnit.MILLISECONDS);

        checkNestedScheduleWithLongerInnerTask(collector, outerTaskDelay, innerTaskDelay);
    }

    @Test
    public void testScheduleWithFixedDelayWithShorterDelayFromWithinATask()
    {
        List<String> collector = new LinkedList<String>();
        long outerTaskDelay = TimeUnit.MINUTES.toMillis(10);
        long innerTaskDelay = TimeUnit.MINUTES.toMillis(20);
        long repeat = TimeUnit.DAYS.toMillis(10);

        executorService.scheduleWithFixedDelay(
                createRunnableWithNestedScheduleWithFixedDelay(collector, innerTaskDelay, repeat),
                outerTaskDelay,
                repeat,
                TimeUnit.MILLISECONDS);

        checkNestedScheduleWithShorterInnerTask(collector, outerTaskDelay, innerTaskDelay);
    }

    @Test
    public void testScheduleRunnableWithLongerDelayFromWithinATask()
    {
        List<String> collector = new LinkedList<String>();
        long outerTaskDelay = TimeUnit.MINUTES.toMillis(10);
        long innerTaskDelay = TimeUnit.MINUTES.toMillis(20);

        executorService.schedule(createRunnableWithNestedSchedule(collector, innerTaskDelay), outerTaskDelay, TimeUnit.MILLISECONDS);

        checkNestedScheduleWithLongerInnerTask(collector, outerTaskDelay, innerTaskDelay);
    }

    @Test
    public void testScheduleRunnableWithShorterDelayFromWithinATask()
    {
        List<String> collector = new LinkedList<String>();
        long outerTaskDelay = TimeUnit.MINUTES.toMillis(10);
        long innerTaskDelay = TimeUnit.MINUTES.toMillis(20);

        executorService.schedule(createRunnableWithNestedSchedule(collector, innerTaskDelay), outerTaskDelay, TimeUnit.MILLISECONDS);

        checkNestedScheduleWithShorterInnerTask(collector, outerTaskDelay, innerTaskDelay);
    }

    @Test
    public void testScheduleCallableWithLongerDelayFromWithinATask()
    {
        List<String> collector = new LinkedList<String>();
        long outerTaskDelay = TimeUnit.MINUTES.toMillis(10);
        long innerTaskDelay = TimeUnit.MINUTES.toMillis(20);

        executorService.schedule(createCallableWithNestedSchedule(collector, innerTaskDelay), outerTaskDelay, TimeUnit.MILLISECONDS);

        checkNestedScheduleWithLongerInnerTask(collector, outerTaskDelay, innerTaskDelay);
    }

    @Test
    public void testScheduleCallableWithShorterDelayFromWithinATask()
    {
        List<String> collector = new LinkedList<String>();
        long outerTaskDelay = TimeUnit.MINUTES.toMillis(10);
        long innerTaskDelay = TimeUnit.MINUTES.toMillis(20);

        executorService.schedule(createCallableWithNestedSchedule(collector, innerTaskDelay), outerTaskDelay, TimeUnit.MILLISECONDS);

        checkNestedScheduleWithShorterInnerTask(collector, outerTaskDelay, innerTaskDelay);
    }

    private Runnable createRunnableWithNestedScheduleWithFixedDelay(final List<String> collector, final long innerTaskDelay, final long repeat)
    {
        return new Runnable()
        {
            @Override
            public void run()
            {
                collector.add(OUTER);
                executorService.scheduleWithFixedDelay(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        collector.add(INNER);
                    }
                }, innerTaskDelay, repeat, TimeUnit.MILLISECONDS);
            }
        };
    }

    private Runnable createRunnableWithNestedScheduleAtFixedRate(final List<String> collector, final long innerTaskDelay, final long repeat)
    {
        return new Runnable()
        {
            @Override
            public void run()
            {
                collector.add(OUTER);
                executorService.scheduleAtFixedRate(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        collector.add(INNER);
                    }
                }, innerTaskDelay, repeat, TimeUnit.MILLISECONDS);
            }
        };
    }

    private Runnable createRunnableWithNestedSchedule(final List<String> collector, final long innerTaskDelay)
    {
        return new Runnable()
        {
            @Override
            public void run()
            {
                collector.add(OUTER);
                executorService.schedule(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        collector.add(INNER);
                    }
                }, innerTaskDelay, TimeUnit.MILLISECONDS);
            }
        };
    }

    private Callable<Boolean> createCallableWithNestedSchedule(final List<String> collector, final long innerTaskDelay)
    {
        return new Callable<Boolean>()
        {
            @Override
            public Boolean call()
            {
                collector.add(OUTER);
                executorService.schedule(new Callable<Boolean>()
                {
                    @Override
                    public Boolean call()
                            throws Exception
                    {
                        collector.add(INNER);
                        return false;
                    }
                }, innerTaskDelay, TimeUnit.MILLISECONDS);
                return false;
            }
        };
    }

    private void checkNestedScheduleWithLongerInnerTask(List<String> collector, long outerTaskDelay, long innerTaskDelay)
    {
        executorService.elapseTime(outerTaskDelay - 1, TimeUnit.MILLISECONDS);
        assertEquals(collector, ImmutableList.of());

        executorService.elapseTime(1, TimeUnit.MILLISECONDS);
        assertEquals(collector, ImmutableList.of(OUTER));

        executorService.elapseTime(innerTaskDelay - 1, TimeUnit.MILLISECONDS);
        assertEquals(collector, ImmutableList.of(OUTER));

        executorService.elapseTime(1, TimeUnit.MILLISECONDS);
        assertEquals(collector, ImmutableList.of(OUTER, INNER));
    }

    private void checkNestedScheduleWithShorterInnerTask(List<String> collector, long outerTaskDelay, long innerTaskDelay)
    {
        executorService.elapseTime(outerTaskDelay + innerTaskDelay, TimeUnit.MILLISECONDS);
        assertEquals(collector, ImmutableList.of(OUTER, INNER));
    }

    static class Counter
            implements Runnable
    {
        private int count = 0;

        @Override
        public void run()
        {
            count++;
        }

        public int getCount()
        {
            return count;
        }
    }

    static class CallableCounter
            implements Callable<Integer>
    {
        private int count = 0;

        @Override
        public Integer call()
                throws Exception
        {
            return ++count;
        }

        public int getCount()
        {
            return count;
        }
    }

    static class FailingCounter
            implements Runnable
    {
        private int count = 0;
        private final int limit;

        FailingCounter(int limit)
        {
            this.limit = limit;
        }

        public int getCount()
        {
            return count;
        }

        @Override
        public void run()
        {
            count++;

            if (count > limit) {
                throw new RuntimeException("deliberate");
            }
        }
    }

    private class TickedCounter extends Counter
    {

        private final Ticker ticker;
        private final long initialTick;
        private final Iterator<Long> expectedTicks;

        public TickedCounter(Ticker ticker, long... expectedTicks)
        {
            this.ticker = ticker;
            initialTick = ticker.read();
            this.expectedTicks = Longs.asList(expectedTicks).iterator();
        }

        @Override
        public void run()
        {
            super.run();
            assertEquals(ticker.read() - initialTick, (long) expectedTicks.next());
        }
    }
}
