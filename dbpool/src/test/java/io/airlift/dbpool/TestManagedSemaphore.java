/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.dbpool;

import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestManagedSemaphore
{
    @Test
    public void testSinglePermit()
            throws InterruptedException
    {
        ManagedSemaphore semaphore = new ManagedSemaphore(1);
        assertPermits(semaphore, 1, 0);

        //
        // tryAcquire()
        //

        // checkout the one permit
        assertTrue(semaphore.tryAcquire());
        assertPermits(semaphore, 1, 1);

        // try to checkout another which fails
        assertFalse(semaphore.tryAcquire());
        assertPermits(semaphore, 1, 1);

        // release it
        semaphore.release();
        assertPermits(semaphore, 1, 0);

        // check it out again
        assertTrue(semaphore.tryAcquire());
        assertPermits(semaphore, 1, 1);

        // try to checkout another which fails
        assertFalse(semaphore.tryAcquire());
        assertPermits(semaphore, 1, 1);

        // release it
        semaphore.release();
        assertPermits(semaphore, 1, 0);


        //
        // tryAcquire(long timeout, TimeUnit unit)
        //

        // checkout the one permit
        assertTrue(semaphore.tryAcquire(1, MILLISECONDS));
        assertPermits(semaphore, 1, 1);

        // try to checkout another which fails
        assertFalse(semaphore.tryAcquire(1, MILLISECONDS));
        assertPermits(semaphore, 1, 1);

        // release it
        semaphore.release();
        assertPermits(semaphore, 1, 0);

        //
        // acquire()
        //

        // checkout the one permit
        semaphore.acquire();
        assertPermits(semaphore, 1, 1);

        // release it
        semaphore.release();
        assertPermits(semaphore, 1, 0);


        //
        // acquireUninterruptibly()
        //

        // checkout the one permit
        semaphore.acquireUninterruptibly();
        assertPermits(semaphore, 1, 1);

        // release it
        semaphore.release();
        assertPermits(semaphore, 1, 0);

        //
        // drainPermits
        //
        assertEquals(semaphore.drainPermits(), 1);
        assertPermits(semaphore, 1, 1);
    }

    @Test
    public void testMultiplePermit()
            throws InterruptedException
    {
        ManagedSemaphore semaphore = new ManagedSemaphore(3);
        assertPermits(semaphore, 3, 0);

        //
        // tryAcquire()
        //

        // checkout the two permits
        assertTrue(semaphore.tryAcquire(2));
        assertPermits(semaphore, 3, 2);

        // try to checkout another two which fails
        assertFalse(semaphore.tryAcquire(2));
        assertPermits(semaphore, 3, 2);

        // release two
        semaphore.release(2);
        assertPermits(semaphore, 3, 0);

        // check two out again
        assertTrue(semaphore.tryAcquire(2));
        assertPermits(semaphore, 3, 2);

        // try to checkout another two which fails
        assertFalse(semaphore.tryAcquire(2));
        assertPermits(semaphore, 3, 2);

        // release two
        semaphore.release(2);
        assertPermits(semaphore, 3, 0);


        //
        // tryAcquire(long timeout, TimeUnit unit)
        //

        // checkout two permits
        assertTrue(semaphore.tryAcquire(2, 1, MILLISECONDS));
        assertPermits(semaphore, 3, 2);

        // try to checkout another two which fails
        assertFalse(semaphore.tryAcquire(2, 1, MILLISECONDS));
        assertPermits(semaphore, 3, 2);

        // release two
        semaphore.release(2);
        assertPermits(semaphore, 3, 0);

        //
        // acquire()
        //

        // checkout two permit
        semaphore.acquire(2);
        assertPermits(semaphore, 3, 2);

        // release two
        semaphore.release(2);
        assertPermits(semaphore, 3, 0);


        //
        // acquireUninterruptibly()
        //

        // checkout two permit
        semaphore.acquireUninterruptibly(2);
        assertPermits(semaphore, 3, 2);

        // release two
        semaphore.release(2);
        assertPermits(semaphore, 3, 0);

        //
        // drainPermits
        //
        assertEquals(semaphore.drainPermits(), 3);
        assertPermits(semaphore, 3, 3);
    }

    @Test
    public void adjustPermits()
    {
        ManagedSemaphore semaphore = new ManagedSemaphore(1);
        assertPermits(semaphore, 1, 0);

        // checkout one permit
        assertTrue(semaphore.tryAcquire());
        assertPermits(semaphore, 1, 1);

        // verify we can't get another
        assertFalse(semaphore.tryAcquire());
        assertPermits(semaphore, 1, 1);

        // increase the available permits to 3 and acquire two extra ones
        semaphore.setPermits(3);
        assertPermits(semaphore, 3, 1);
        assertTrue(semaphore.tryAcquire(2));
        assertPermits(semaphore, 3, 3);

        // verify we can't get another
        assertFalse(semaphore.tryAcquire());
        assertPermits(semaphore, 3, 3);

        // reduce the number of permits to two and verify we sill can't get another one
        semaphore.setPermits(2);
        assertPermits(semaphore, 2, 3);
        assertFalse(semaphore.tryAcquire());
        assertPermits(semaphore, 2, 3);

        // now release one and verify we still can't get another one
        semaphore.release();
        assertPermits(semaphore, 2, 2);
        assertFalse(semaphore.tryAcquire());
        assertPermits(semaphore, 2, 2);

        // finally release another one and verify we can reacquire it
        semaphore.release();
        assertPermits(semaphore, 2, 1);
        assertTrue(semaphore.tryAcquire());
        assertPermits(semaphore, 2, 2);

        // release the permits
        semaphore.release(2);
        assertPermits(semaphore, 2, 0);
    }

    private void assertPermits(ManagedSemaphore semaphore, int totalPermits, int checkedOutPermits)
    {
        assertEquals(semaphore.getPermits(), totalPermits);
        assertEquals(semaphore.getActivePermits(), checkedOutPermits);
        assertEquals(semaphore.getAvailablePermits(), totalPermits - checkedOutPermits);
        assertEquals(semaphore.availablePermits(), totalPermits - checkedOutPermits);
    }
}
