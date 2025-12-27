package io.airlift.mcp;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static java.util.Objects.requireNonNull;

public record SleepToolController(Semaphore startedLatch, BlockingQueue<String> namesThatShouldExit, BlockingQueue<String> namesThatHaveExited)
{
    public SleepToolController
    {
        requireNonNull(startedLatch, "startedLatch is null");
        requireNonNull(namesThatShouldExit, "namesThatShouldExit is null");
        requireNonNull(namesThatHaveExited, "namesThatHaveExited is null"); // don't copy
    }

    public static SleepToolController instance()
    {
        return new SleepToolController(new Semaphore(0), new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>());
    }

    public void reset()
    {
        startedLatch.drainPermits();
        namesThatShouldExit.clear();
        namesThatHaveExited.clear();
    }
}
