package io.airlift.stats;

import com.google.common.base.Ticker;
import com.google.errorprone.annotations.ThreadSafe;
import org.weakref.jmx.Managed;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
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
    private final DecayState decay;

    private double count;

    public DecayCounter(double alpha)
    {
        this(DecayConfig.of(alpha));
    }

    public DecayCounter(double alpha, Ticker ticker)
    {
        this(DecayConfig.of(alpha, ticker));
    }

    public DecayCounter(DecayConfig config)
    {
        this(0, requireNonNull(config, "config is null").newState());
    }

    private DecayCounter(double count, DecayState decay)
    {
        this.count = count;
        this.decay = decay;
    }

    public synchronized DecayCounter duplicate()
    {
        return new DecayCounter(count, decay.copy());
    }

    public synchronized void add(long value)
    {
        long nowInSeconds = decay.nowInSeconds();

        if (decay.needsRescale(nowInSeconds)) {
            count /= decay.rescaleTo(nowInSeconds);
        }
        count += value * decay.weightAt(nowInSeconds);
    }

    public void merge(DecayCounter decayCounter)
    {
        requireNonNull(decayCounter, "decayCounter is null");

        // Snapshot the other counter under its own monitor, then apply the merge under ours. Holding
        // only one monitor at a time avoids the deadlock that nested locking allows when a.merge(b)
        // and b.merge(a) run concurrently.
        long otherLandmarkInSeconds;
        double otherCount;
        double otherAlpha;
        synchronized (decayCounter) {
            otherAlpha = decayCounter.decay.getAlpha();
            otherLandmarkInSeconds = decayCounter.decay.getLandmarkInSeconds();
            otherCount = decayCounter.count;
        }

        checkArgument(otherAlpha == decay.getAlpha(), "Expected decayCounter to have alpha %s, but was %s", decay.getAlpha(), otherAlpha);

        synchronized (this) {
            // if this counter's landmark is behind the other counter
            if (decay.getLandmarkInSeconds() < otherLandmarkInSeconds) {
                // rescale this counter to the other counter, and add
                count /= decay.rescaleTo(otherLandmarkInSeconds);
                count += otherCount;
            }
            else {
                // rescale the other counter's value (without mutating it) and add
                count += otherCount / decay.weightFromLandmark(otherLandmarkInSeconds);
            }
        }
    }

    @Managed
    public synchronized void reset()
    {
        decay.setLandmarkInSeconds(decay.nowInSeconds());
        count = 0;
    }

    /**
     * This is a hack to work around limitations in Jmxutils.
     */
    @Deprecated
    public synchronized void resetTo(DecayCounter counter)
    {
        synchronized (counter) {
            decay.setLandmarkInSeconds(counter.decay.getLandmarkInSeconds());
            count = counter.count;
        }
    }

    @Managed
    public synchronized double getCount()
    {
        return count / decay.currentWeight();
    }

    @Managed
    public synchronized double getRate()
    {
        // The total time covered by this counter is equivalent to the integral of the weight function from 0 to Infinity,
        // which equals 1/alpha. The count per unit time is, therefore, count / (1/alpha)
        return getCount() * decay.getAlpha();
    }

    public DecayCounterSnapshot snapshot()
    {
        // synchronization on getCount() is sufficient
        double count = getCount();
        return new DecayCounterSnapshot(count, count * decay.getAlpha());
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
        return decay.getAlpha();
    }

    public record DecayCounterSnapshot(double count, double rate) {}
}
