package com.proofpoint.concurrent.events;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.log.Logger;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Quantizes the running of a command based on time. Prevents a command from running more than once
 * per interval. Call {@link #makeRunnable()} whenever you want the command to run. If the task has not been
 * run within the interval, it will be scheduled for a run when the next interval arrives. Future requests
 * to run the command during this time will be ignored.
 */
public class QuantizedExecutor
{
    private final long                      intervalTimeInMs;
    private final ExecutorService           executor;
    @SuppressWarnings({ "MismatchedQueryAndUpdateOfCollection" })
    private final DelayQueue<RunType>       runnerQueue = new DelayQueue<RunType>();
    private final Runnable                  command;
    private final Logger                    log;
    private final AtomicBoolean             isStarted = new AtomicBoolean(true);
    private final AtomicReference<RunState> runState = new AtomicReference<RunState>(RunState.NOT_RUNNING);
    private final AtomicBoolean             isActive = new AtomicBoolean(false);

    private static class RunType implements Delayed
    {
        private final long runTimeInMS;

        private RunType(long runTimeInMS)
        {
            this.runTimeInMS = runTimeInMS;
        }

        @Override
        public long getDelay(TimeUnit unit)
        {
            long    remaining = runTimeInMS - System.currentTimeMillis();
            return unit.convert(remaining, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o)
        {
            long    diff = getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
            return Long.signum(diff);
        }
    }

    private enum RunState
    {
        NOT_RUNNING,
        SCHEDULED,
        RUN_IMMEDIATELY
    }

    /**
     * @param intervalTimeInMs number of ticks to wait after a run request before performing the run
     * @param command the command to run
     */
    public QuantizedExecutor(long intervalTimeInMs, Runnable command)
    {
        this.command = command;
        this.intervalTimeInMs = intervalTimeInMs;
        log = Logger.get(command.getClass());

        ThreadFactory factory = new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("QuantizedExecutor for " + command.getClass().getName())
                // TODO: .setUncaughtExceptionHandler()
                .build();

        executor = Executors.newSingleThreadExecutor(factory);
        executor.submit(new Runner());
    }

    /**
     * Return true if the executor's run loop is active
     *
     * @return true/false
     */
    public boolean isActive()
    {
        return isActive.get();
    }

    /**
     * Shutdown the executor
     */
    public void     shutdown()
    {
        if ( isStarted.compareAndSet(true, false) )
        {
            executor.shutdownNow();
        }
    }

    /**
     * Denote that a run is needed
     */
    public void makeRunnable()
    {
        if ( isStarted.get() )
        {
            if ( runState.compareAndSet(RunState.NOT_RUNNING, RunState.SCHEDULED) )
            {
                runnerQueue.offer(new RunType(System.currentTimeMillis() + intervalTimeInMs));
            }
        }
    }

    /**
     * if the command isn't currently running, the command is scheduled with an internal ticks of 0
     */
    public void runNowIf()
    {
        if ( isStarted.get() )
        {
            if ( runState.getAndSet(RunState.RUN_IMMEDIATELY) != RunState.RUN_IMMEDIATELY ) // i.e. it wasn't already set to run immediately
            {
                runnerQueue.offer(new RunType(System.currentTimeMillis() - 1));
            }
        }
    }

    private class Runner implements Runnable
    {
        @Override
        public void run()
        {
            isActive.set(true);
            try
            {
                while ( !Thread.interrupted() )
                {
                    // take the currently expiring event - either intervalTimeInMs or immediate
                    runnerQueue.take();
                    runnerQueue.clear();    // clear any future requests - we're about to run

                    runState.set(RunState.NOT_RUNNING);     // atomically set to not running _before_ running the command to avoid race condition of another run request coming in
                    command.run();
                }
            }
            catch ( InterruptedException e )
            {
                Thread.currentThread().interrupt();
            }
            catch ( Throwable e )
            {
                log.error(e, "Caught by QuantizedExecutor");
                throw new Error(e);
            }
            finally
            {
                isActive.set(false);
            }
        }
    }
}
