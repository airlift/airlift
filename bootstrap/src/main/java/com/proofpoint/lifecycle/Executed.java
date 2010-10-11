package com.proofpoint.lifecycle;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Extend this class to get run-in-thread behavior
 */
public abstract class Executed implements Runnable
{
    private final ExecutorService       executor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void     startExecution()
    {
        executor.submit(this);
    }

    @PreDestroy
    public void     stopExecution()
    {
        executor.shutdownNow();
    }
}
