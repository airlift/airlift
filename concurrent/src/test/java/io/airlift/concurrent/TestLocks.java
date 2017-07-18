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
package io.airlift.concurrent;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.concurrent.Locks.locking;
import static io.airlift.concurrent.Locks.lockingInterruptibly;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestLocks
{
    private ExecutorService executor;

    @BeforeClass
    public void setUp()
    {
        executor = newCachedThreadPool();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
    {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * This test ensures there is no overload selection ambiguity at compilation time.
     */
    @Test
    public void testLockingCompilation()
    {
        Lock lock = new ReentrantLock();

        locking(lock, () -> {
        });

        locking(lock, () -> "foo");
        String stringResult = locking(lock, () -> "foo");

        try {
            lockingInterruptibly(lock, () -> {
            });
        }
        catch (InterruptedException ignore) {
        }

        try {
            lockingInterruptibly(lock, () -> "foo");
        }
        catch (InterruptedException ignore) {
        }
    }

    @Test
    public void testLockingWithReturnedValue()
    {
        assertEquals(locking(new ReentrantLock(), () -> "foo"), "foo");
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "exception thrown under a lock")
    public void testLockingWithRuntimeException()
    {
        locking(new ReentrantLock(), () -> {
            throw new RuntimeException("exception thrown under a lock");
        });
    }

    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = "exception thrown under a lock")
    public void testLockingWithCheckedException()
            throws IOException
    {
        locking(new ReentrantLock(), () -> {
            throw new IOException("exception thrown under a lock");
        });
    }

    @Test
    public void testLockingLocks()
            throws Exception
    {
        Lock lock = new ReentrantLock();
        int threads = 10;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger underLock = new AtomicInteger(0);
        AtomicInteger maxUnderLock = new AtomicInteger(0);

        List<Future<?>> futures = IntStream.range(0, threads)
                .mapToObj(i -> executor.submit(() -> {
                    barrier.await();
                    locking(lock, () -> {
                        maxUnderLock.accumulateAndGet(underLock.incrementAndGet(), Integer::max);
                        Thread.sleep(30);
                        underLock.decrementAndGet();
                    });

                    return null;
                }))
                .collect(toImmutableList());

        futures.forEach(MoreFutures::getFutureValue);
        assertEquals(maxUnderLock.get(), 1, "More than one thread acquired the lock at the same time");
    }

    @Test
    public void testLockingInterruptiblyCanBeInterrupted()
            throws Exception
    {
        Lock lock = new ReentrantLock();
        Exchanger<Thread> exchanger = new Exchanger<>();

        Future<Object> future = executor.submit(() -> {
            exchanger.exchange(Thread.currentThread());
            // main thread is under lock now
            lockingInterruptibly(lock, () -> {
                throw new AssertionError("test background thread got the lock");
            });
            throw new AssertionError("lockingInterruptibly returned, InterruptedException was expected");
        });

        locking(lock, () -> {
            Thread testBackgroundThread = exchanger.exchange(null, 5, SECONDS);
            Thread.sleep(40); // give the thread chance to lock
            testBackgroundThread.interrupt();
            // we can release lock now, as Lock.lockInterruptibly throws interrupted even if lock is available
        });

        try {
            future.get(5, SECONDS);
            fail("ExecutionException was expected");
        }
        catch (ExecutionException expected) {
            if (!(expected.getCause() instanceof InterruptedException)) {
                fail("Expected failure caused by interruption", expected);
            }
        }
    }
}
