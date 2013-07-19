package io.airlift.discovery.client;

import io.airlift.log.Logger;
import io.airlift.units.Duration;

import javax.annotation.concurrent.GuardedBy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class ExponentialBackOff
{
    private final long initialWait;
    private final long maxWait;
    private final String serverUpMessage;
    private final String serverDownMessage;
    private final Logger log;

    @GuardedBy("this")
    private boolean serverUp = true;

    @GuardedBy("this")
    private long currentWaitInMillis = -1;

    public ExponentialBackOff(Duration initialWait, Duration maxWait, String serverUpMessage, String serverDownMessage, Logger log)
    {
        this.initialWait = checkNotNull(initialWait, "initialWait is null").toMillis();
        this.maxWait = checkNotNull(maxWait, "maxWait is null").toMillis();
        checkArgument(this.initialWait <= this.maxWait, "initialWait %s is less than maxWait %s", initialWait, maxWait);

        this.serverUpMessage = checkNotNull(serverUpMessage, "serverUpMessage is null");
        this.serverDownMessage = checkNotNull(serverDownMessage, "serverDownMessage is null");
        this.log = checkNotNull(log, "log is null");
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
            serverUp = false;
            log.error("%s: %s", serverDownMessage, t.getMessage());
        }
        log.debug(t, serverDownMessage);

        if (currentWaitInMillis <= 0) {
            currentWaitInMillis = initialWait;
        }
        else {
            currentWaitInMillis = Math.min(currentWaitInMillis * 2, maxWait);
        }
        return new Duration(currentWaitInMillis, MILLISECONDS);
    }
}
