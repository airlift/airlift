package com.proofpoint.bootstrap;

import com.google.inject.Inject;

import java.util.concurrent.CountDownLatch;

public class ExecutedInstance extends Executed
{
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch endLatch = new CountDownLatch(1);

    @Inject
    public ExecutedInstance()
    {
    }

    public void waitForStart()
            throws InterruptedException
    {
        startLatch.await();
    }

    public void waitForEnd()
            throws InterruptedException
    {
        endLatch.await();
    }

    @Override
    public void run()
    {
        TestLifeCycleManager.note("Starting");
        startLatch.countDown();

        try {
            if (!Thread.interrupted()) {
                try {
                    Thread.sleep(Integer.MAX_VALUE);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        finally {
            TestLifeCycleManager.note("Done");
            endLatch.countDown();
        }
    }
}
