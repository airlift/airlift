package com.proofpoint.lifecycle;

import com.google.inject.Inject;

import java.util.concurrent.CountDownLatch;

public class ExecutedInstance extends Executed
{
    private final CountDownLatch        latch = new CountDownLatch(1);

    @Inject
    public ExecutedInstance()
    {
    }

    public void     waitForRun() throws InterruptedException
    {
        latch.await();
    }

    @Override
    public void run()
    {
        TestLifeCycleManager.note("Starting");
        latch.countDown();
        if ( !Thread.interrupted() )
        {
            try
            {
                Thread.sleep(Integer.MAX_VALUE);
            }
            catch ( InterruptedException e )
            {
                Thread.currentThread().interrupt();
            }
        }
        TestLifeCycleManager.note("Done");
    }
}
