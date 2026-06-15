package io.airlift.stats;

import com.google.common.base.Ticker;
import com.google.errorprone.annotations.ThreadSafe;
import org.weakref.jmx.Managed;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.locks.StampedLock;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.stats.ExponentialDecay.weight;
import static java.util.Objects.requireNonNull;

/*
 * A counter that decays exponentially. Values are weighted according to the formula
 *     w(t, α) = e^(-α * t), where α is the decay factor and t is the age in seconds
 *
 * The implementation is based on the ideas from
 * http://dimacs.rutgers.edu/~graham/pubs/papers/fwddecay.pdf
 * to not have to rely on a timer that decays the value periodically
 */
@ThreadSafe
public final class DecayCounter
{
    // needs to be such that Math.exp(alpha * seconds) does not grow too big
    static final long RESCALE_THRESHOLD_SECONDS = 50;

    private final double alpha;
    private final Ticker ticker;

    // Adds run under the read lock and accumulate into the adder concurrently. Operations
    // that rescale or replace the accumulated count (rescale, merge, reset) require the
    // write lock: DoubleAdder.sumThenReset() loses concurrent updates, so adds must be
    // excluded while it runs.
    private final StampedLock lock = new StampedLock();
    private final DoubleAdder count = new DoubleAdder();
    // written only under the write lock, read under the read lock
    private long landmarkInSeconds;
    // benign race: concurrent writers store the same value for the same (now, landmark)
    private volatile CachedWeight cachedWeight;

    public DecayCounter(double alpha)
    {
        this(alpha, Ticker.systemTicker());
    }

    public DecayCounter(double alpha, Ticker ticker)
    {
        this(0, alpha, ticker, TimeUnit.NANOSECONDS.toSeconds(ticker.read()));
    }

    private DecayCounter(double initialCount, double alpha, Ticker ticker, long landmarkInSeconds)
    {
        this.count.add(initialCount);
        this.alpha = alpha;
        this.ticker = ticker;
        this.landmarkInSeconds = landmarkInSeconds;
        this.cachedWeight = new CachedWeight(landmarkInSeconds, landmarkInSeconds, 1.0);
    }

    public DecayCounter duplicate()
    {
        long stamp = lock.readLock();
        try {
            return new DecayCounter(count.sum(), alpha, ticker, landmarkInSeconds);
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    public void add(long value)
    {
        add(value, getTickInSeconds());
    }

    void add(long value, long nowInSeconds)
    {
        long stamp = lock.readLock();
        try {
            if (nowInSeconds - landmarkInSeconds >= RESCALE_THRESHOLD_SECONDS) {
                long writeStamp = lock.tryConvertToWriteLock(stamp);
                if (writeStamp == 0) {
                    lock.unlockRead(stamp);
                    writeStamp = lock.writeLock();
                }
                stamp = writeStamp;
                // recheck: another thread may have rescaled while we waited for the write lock
                if (nowInSeconds - landmarkInSeconds >= RESCALE_THRESHOLD_SECONDS) {
                    rescaleToNewLandmark(nowInSeconds);
                }
            }
            count.add(value * weightAt(nowInSeconds));
        }
        finally {
            lock.unlock(stamp);
        }
    }

    public void merge(DecayCounter decayCounter)
    {
        requireNonNull(decayCounter, "decayCounter is null");
        checkArgument(decayCounter.alpha == alpha, "Expected decayCounter to have alpha %s, but was %s", alpha, decayCounter.alpha);

        double otherCount;
        long otherLandmarkInSeconds;
        long otherStamp = decayCounter.lock.readLock();
        try {
            otherCount = decayCounter.count.sum();
            otherLandmarkInSeconds = decayCounter.landmarkInSeconds;
        }
        finally {
            decayCounter.lock.unlockRead(otherStamp);
        }

        long stamp = lock.writeLock();
        try {
            // if the landmark this counter is behind the other counter
            if (landmarkInSeconds < otherLandmarkInSeconds) {
                // rescale this counter to the other counter, and add
                rescaleToNewLandmark(otherLandmarkInSeconds);
                count.add(otherCount);
            }
            else {
                // rescale the other counter and add
                count.add(otherCount / weight(alpha, landmarkInSeconds, otherLandmarkInSeconds));
            }
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    private void rescaleToNewLandmark(long newLandMarkInSeconds)
    {
        // rescale the count based on a new landmark to avoid numerical overflow issues
        double oldCount = count.sumThenReset();
        count.add(oldCount / weight(alpha, newLandMarkInSeconds, landmarkInSeconds));
        landmarkInSeconds = newLandMarkInSeconds;
    }

    @Managed
    public void reset()
    {
        long nowInSeconds = getTickInSeconds();
        long stamp = lock.writeLock();
        try {
            landmarkInSeconds = nowInSeconds;
            count.reset();
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * This is a hack to work around limitations in Jmxutils.
     */
    @Deprecated
    public void resetTo(DecayCounter counter)
    {
        double otherCount;
        long otherLandmarkInSeconds;
        long otherStamp = counter.lock.readLock();
        try {
            otherCount = counter.count.sum();
            otherLandmarkInSeconds = counter.landmarkInSeconds;
        }
        finally {
            counter.lock.unlockRead(otherStamp);
        }

        long stamp = lock.writeLock();
        try {
            landmarkInSeconds = otherLandmarkInSeconds;
            count.reset();
            count.add(otherCount);
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    @Managed
    public double getCount()
    {
        long nowInSeconds = getTickInSeconds();
        long stamp = lock.readLock();
        try {
            return count.sum() / weightAt(nowInSeconds);
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    @Managed
    public double getRate()
    {
        // The total time covered by this counter is equivalent to the integral of the weight function from 0 to Infinity,
        // which equals 1/alpha. The count per unit time is, therefore, count / (1/alpha)
        return getCount() * alpha;
    }

    // The weight depends only on second-granularity timestamps, so within a given second
    // every add recomputes the same value. Caching it keeps Math.exp off the hot path.
    private double weightAt(long nowInSeconds)
    {
        long landmark = landmarkInSeconds;
        CachedWeight cached = cachedWeight;
        if (cached.nowInSeconds == nowInSeconds && cached.landmarkInSeconds == landmark) {
            return cached.weight;
        }
        double computedWeight = weight(alpha, nowInSeconds, landmark);
        cachedWeight = new CachedWeight(nowInSeconds, landmark, computedWeight);
        return computedWeight;
    }

    long getTickInSeconds()
    {
        return TimeUnit.NANOSECONDS.toSeconds(ticker.read());
    }

    public DecayCounterSnapshot snapshot()
    {
        // synchronization on getCount() is sufficient
        double count = getCount();
        return new DecayCounterSnapshot(count, count * alpha);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("count", getCount())
                .add("rate", getRate())
                .toString();
    }

    public double getAlpha()
    {
        return alpha;
    }

    public record DecayCounterSnapshot(double count, double rate) {}

    private record CachedWeight(long nowInSeconds, long landmarkInSeconds, double weight) {}
}
