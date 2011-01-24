package com.proofpoint.dbpool;

import org.testng.annotations.Test;
import static org.testng.AssertJUnit.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

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

        // checout the one permit
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

        // checout the one permit
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

        // checout the one permit
        semaphore.acquire();
        assertPermits(semaphore, 1, 1);

        // release it
        semaphore.release();
        assertPermits(semaphore, 1, 0);


        //
        // acquireUninterruptibly()
        //

        // checout the one permit
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

        // checout the two permits
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

        // checout two permits
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

        // checout two permit
        semaphore.acquire(2);
        assertPermits(semaphore, 3, 2);

        // release two
        semaphore.release(2);
        assertPermits(semaphore, 3, 0);


        //
        // acquireUninterruptibly()
        //

        // checout two permit
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

        // checout one permit
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

    private void assertPermits(ManagedSemaphore semaphore, int totalPermits, int checkedoutPermits)
    {
        assertEquals(semaphore.getPermits(), totalPermits);
        assertEquals(semaphore.getActivePermits(), checkedoutPermits);
        assertEquals(semaphore.getAvailablePermits(), totalPermits - checkedoutPermits);
        assertEquals(semaphore.availablePermits(), totalPermits - checkedoutPermits);
    }
}
