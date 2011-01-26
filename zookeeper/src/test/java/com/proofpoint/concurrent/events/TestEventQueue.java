package com.proofpoint.concurrent.events;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TestEventQueue
{
    private enum MyEvent implements EventQueue.Event<MyEvent>
    {
        ONE(),
        TWO(),
        THREE()
        ;


        private MyEvent()
        {
        }

        @Override
        public boolean canBeMergedWith(MyEvent event)
        {
            return equals(event);
        }

        @Override
        public void processEvent()
        {
        }
    }

    @Test
    public void     testZero() throws InterruptedException
    {
        EventQueue<MyEvent>     queue = new EventQueue<MyEvent>(0);
        final CountDownLatch    latch = new CountDownLatch(1);
        queue.addListener(new EventQueue.EventListener<com.proofpoint.concurrent.events.TestEventQueue.MyEvent>()
        {
            @Override
            public void eventProcessed(MyEvent event) throws Exception
            {
                latch.countDown();
            }
        });

        queue.postEvent(MyEvent.ONE);

        Assert.assertTrue(latch.await(1, TimeUnit.MINUTES));
    }

    @Test
    public void     testMerge() throws InterruptedException
    {
        final AtomicInteger     count = new AtomicInteger(0);
        final CountDownLatch    latch = new CountDownLatch(1);
        EventQueue<MyEvent>     queue = new EventQueue<MyEvent>(1000);
        queue.addListener(new EventQueue.EventListener<com.proofpoint.concurrent.events.TestEventQueue.MyEvent>()
        {
            @Override
            public void eventProcessed(MyEvent event) throws Exception
            {
                count.incrementAndGet();
                if ( event == MyEvent.TWO )
                {
                    latch.countDown();
                }
            }
        });

        queue.postEvent(MyEvent.ONE);
        queue.postEvent(MyEvent.ONE);
        queue.postEvent(MyEvent.ONE);
        queue.postEvent(MyEvent.TWO);

        Assert.assertTrue(latch.await(1, TimeUnit.MINUTES));
        Assert.assertEquals(count.get(), 2);
    }

    @Test
    public void     testForce() throws InterruptedException
    {
        final Semaphore         count = new Semaphore(0);
        EventQueue<MyEvent>     queue = new EventQueue<MyEvent>(10000000);
        queue.addListener(new EventQueue.EventListener<com.proofpoint.concurrent.events.TestEventQueue.MyEvent>()
        {
            @Override
            public void eventProcessed(MyEvent event) throws Exception
            {
                count.release();
            }
        });

        queue.postEvent(MyEvent.ONE);

        Assert.assertEquals(count.availablePermits(), 0);

        queue.forceQueue();

        Assert.assertTrue(count.tryAcquire(1, 1, TimeUnit.MINUTES));
    }
    
    @Test
    public void     testPause() throws InterruptedException
    {
        final Semaphore         count = new Semaphore(0);
        EventQueue<MyEvent>     queue = new EventQueue<MyEvent>(100);
        queue.addListener(new EventQueue.EventListener<com.proofpoint.concurrent.events.TestEventQueue.MyEvent>()
        {
            @Override
            public void eventProcessed(MyEvent event) throws Exception
            {
                count.release();
            }
        });

        queue.pauseQueue();

        queue.postEvent(MyEvent.ONE);

        Assert.assertEquals(count.availablePermits(), 0);

        queue.resumeQueue();

        Assert.assertTrue(count.tryAcquire(1, 1, TimeUnit.MINUTES));
    }

    @Test
    public void     testTime() throws InterruptedException
    {
        final int               waitTime = 1000;
        EventQueue<MyEvent>     queue = new EventQueue<MyEvent>(waitTime);
        final AtomicLong        processedTime = new AtomicLong(0);
        long                    startTime = System.currentTimeMillis();
        final CountDownLatch    latch = new CountDownLatch(1);
        queue.addListener(new EventQueue.EventListener<com.proofpoint.concurrent.events.TestEventQueue.MyEvent>()
        {
            @Override
            public void eventProcessed(MyEvent event) throws Exception
            {
                processedTime.set(System.currentTimeMillis());
                latch.countDown();
            }
        });
        queue.postEvent(MyEvent.ONE);
        Assert.assertTrue(latch.await(1, TimeUnit.MINUTES));
        long        elapsed = processedTime.get() - startTime;
        Assert.assertTrue(elapsed >= waitTime);
    }

    @Test
    public void     testBasic() throws InterruptedException
    {
        EventQueue<MyEvent>     queue = new EventQueue<MyEvent>(100);

        final Set<MyEvent>      eventSet = Collections.newSetFromMap(new ConcurrentHashMap<MyEvent, Boolean>());
        final CountDownLatch    latch = new CountDownLatch(1);
        queue.addListener(new EventQueue.EventListener<com.proofpoint.concurrent.events.TestEventQueue.MyEvent>()
        {
            @Override
            public void eventProcessed(MyEvent event) throws Exception
            {
                eventSet.add(event);
                synchronized(TestEventQueue.this)
                {
                    if ( eventSet.size() == MyEvent.values().length )
                    {
                        latch.countDown();
                    }
                }
            }
        });

        queue.postEvent(MyEvent.ONE);
        queue.postEvent(MyEvent.TWO);
        queue.postEvent(MyEvent.THREE);

        Assert.assertTrue(latch.await(1, TimeUnit.MINUTES));
    }
}
