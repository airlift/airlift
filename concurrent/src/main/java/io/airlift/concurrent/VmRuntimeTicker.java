package io.airlift.concurrent;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Ticker;
import io.airlift.log.Logger;
import io.airlift.stats.TimeStat;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A ticker that does not advance when the VM is paused.
 *
 * This is implemented using a simple background thread that sleeps for a small
 * period and then measures the sleep duration.  If the duration is small also,
 * the ticker is advanced; otherwise it is ignored.
 *
 * The pause durations are recorded in a TimeStat for monitoring.
 */
public class VmRuntimeTicker
        extends Ticker
{
    private static final Logger log = Logger.get(VmRuntimeTicker.class);

    private static final Duration DEFAULT_LOOP_SLEEP_MILLIS = new Duration(1, MILLISECONDS);
    private static final Duration DEFAULT_MAX_SAFE_VM_PAUSE_NANOS = new Duration(100, MILLISECONDS);

    private static final Supplier<Ticker> VM_RUNTIME_TICKER = Suppliers.memoize(() -> {
        VmRuntimeTicker ticker = new VmRuntimeTicker(Ticker.systemTicker());
        ticker.start();

        // return a wrapper so shared ticker can not be stopped
        return new Ticker()
        {
            @Override
            public long read()
            {
                return ticker.read();
            }
        };
    });

    public static Ticker getVmRuntimeTicker()
    {
        return VM_RUNTIME_TICKER.get();
    }

    private final Ticker systemTicker;
    private final long loopSleepMillis;
    private final long maxSafeVmPauseNanos;

    private final Thread vmRuntimeMonitorThread;

    private final AtomicLong currentValue = new AtomicLong();

    private final AtomicLong totalPauseNanos = new AtomicLong();
    private final TimeStat pauseTime = new TimeStat();

    // public and volatile to assure the allocation is not optimized away
    @SuppressWarnings({"WeakerAccess", "PublicField"})
    public volatile Long sleepObjectAllocation;

    private final AtomicBoolean badSystemNanoTime = new AtomicBoolean();

    public VmRuntimeTicker(Ticker systemTicker)
    {
        this(systemTicker, DEFAULT_LOOP_SLEEP_MILLIS, DEFAULT_MAX_SAFE_VM_PAUSE_NANOS);
    }

    public VmRuntimeTicker(Ticker systemTicker, Duration loopSleep, Duration maxSafeVmPause)
    {
        this.systemTicker = requireNonNull(systemTicker, "systemTicker is null");

        this.loopSleepMillis = requireNonNull(loopSleep, "loopSleep is null").toMillis();
        checkArgument(loopSleepMillis > 0, "loopSleep must be at least 1 millis second");

        this.maxSafeVmPauseNanos = requireNonNull(maxSafeVmPause, "maxSafeVmPause is null").roundTo(NANOSECONDS);
        checkArgument(maxSafeVmPauseNanos > MILLISECONDS.toNanos(loopSleepMillis) * 2, "maxSafeVmPause must be at least 2 times larger than loopSleep");

        vmRuntimeMonitorThread = new Thread(this::updateLoop, "VM Runtime Monitor");
        vmRuntimeMonitorThread.setDaemon(true);
    }

    public void start()
    {
        vmRuntimeMonitorThread.start();
    }

    public void stop()
    {
        vmRuntimeMonitorThread.interrupt();
    }

    @Managed
    public long getTotalPauseMillis()
    {
        return NANOSECONDS.toMillis(totalPauseNanos.get());
    }

    @Managed
    public TimeStat getPauseTime()
    {
        return pauseTime;
    }

    @Override
    public long read()
    {
        return currentValue.get();
    }

    private void updateLoop()
    {
        long lastRead = systemTicker.read();
        while (true) {
            try {
                Thread.sleep(loopSleepMillis);

                // Allocate an object to make sure potential allocation stalls are measured.
                sleepObjectAllocation = lastRead;

                long currentRead = systemTicker.read();

                long sleepDuration = currentRead - lastRead;
                lastRead = currentRead;

                // check if clock moved backwards
                if (sleepDuration < 0) {
                    if (badSystemNanoTime.compareAndSet(false, true)) {
                        log.warn("Possible kernel or JVM bug: system nano time moved backwards");
                    }
                    continue;
                }

                long sleepDelta = sleepDuration - loopSleepMillis;

                // check if thread was woken up early
                if (sleepDelta < 0) {
                    continue;
                }

                update(sleepDuration);
            }
            catch (InterruptedException e) {
                return;
            }
            catch (RuntimeException | Error e) {
                log.error("Exception while monitoring VM runtime", e);
            }
        }
    }

    @VisibleForTesting
    void update(long deltaNanos)
    {
        if (deltaNanos < 0) {
            // clock moved backwards ignore
            if (badSystemNanoTime.compareAndSet(false, true)) {
                log.warn("Possible kernel or JVM bug: system nano time moved backwards");
            }
        }
        else if (deltaNanos > maxSafeVmPauseNanos) {
            totalPauseNanos.addAndGet(deltaNanos);
            pauseTime.addNanos(deltaNanos);
        }
        else {
            // add the delta
            currentValue.addAndGet(deltaNanos);
        }
    }
}
