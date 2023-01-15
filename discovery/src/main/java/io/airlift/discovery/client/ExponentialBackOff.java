package io.airlift.discovery.client;

import io.airlift.log.Logger;
import io.airlift.units.Duration;

import javax.annotation.concurrent.GuardedBy;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class ExponentialBackOff
{
    private static final long ERROR_LOGGING_DELAY_NANOS = MILLISECONDS.toNanos(500);

    private final long initialWait;
    private final long maxWait;
    private final String serverUpMessage;
    private final String serverDownMessage;
    private final Logger log;

    private final long requestStart = System.nanoTime();

    @GuardedBy("this")
    private boolean serverUp = true;

    @GuardedBy("this")
    private long currentWaitInMillis = -1;

    public ExponentialBackOff(Duration initialWait, Duration maxWait, String serverUpMessage, String serverDownMessage, Logger log)
    {
        this.initialWait = requireNonNull(initialWait, "initialWait is null").toMillis();
        this.maxWait = requireNonNull(maxWait, "maxWait is null").toMillis();
        checkArgument(this.initialWait <= this.maxWait, "initialWait %s is less than maxWait %s", initialWait, maxWait);

        this.serverUpMessage = requireNonNull(serverUpMessage, "serverUpMessage is null");
        this.serverDownMessage = requireNonNull(serverDownMessage, "serverDownMessage is null");
        this.log = requireNonNull(log, "log is null");
    }

    public synchronized void success()
    {
        if (!serverUp) {
            serverUp = true;
            log.info(serverUpMessage);
        }
        currentWaitInMillis = -1;
    }

    public synchronized Duration failed(Throwable t)
    {
        if (serverUp) {
            // skip logging until 500ms has passed
            if ((System.nanoTime() - requestStart) >= ERROR_LOGGING_DELAY_NANOS) {
                serverUp = false;
                log.error(t, serverDownMessage);
            }
        }

        if (currentWaitInMillis <= 0) {
            currentWaitInMillis = initialWait;
        }
        else {
            currentWaitInMillis = Math.min(currentWaitInMillis * 2, maxWait);
        }
        return new Duration(currentWaitInMillis, MILLISECONDS);
    }
}
