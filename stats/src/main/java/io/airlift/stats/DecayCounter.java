package io.airlift.stats;

import com.google.common.base.Ticker;
import com.google.errorprone.annotations.ThreadSafe;
import jakarta.annotation.Nullable;
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
    @Nullable
    private final DecayState decay;

    private double count;

    public DecayCounter(double alpha)
    {
        this(alpha == 0.0 ? null : DecayConfig.of(alpha));
    }

    public DecayCounter(double alpha, Ticker ticker)
    {
        this(alpha == 0.0 ? null : DecayConfig.of(alpha, ticker));
    }

    /**
     * @param config the decay configuration, or null for a counter that does not decay
     */
    public DecayCounter(@Nullable DecayConfig config)
    {
        this(0, config == null ? null : config.newState());
    }

    private DecayCounter(double count, @Nullable DecayState decay)
    {
        this.count = count;
        this.decay = decay;
    }

    public synchronized DecayCounter duplicate()
    {
        return new DecayCounter(count, decay == null ? null : decay.copy());
    }

    public synchronized void add(long value)
    {
        if (decay == null) {
            count += value;
            return;
        }

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
            otherAlpha = decayCounter.getAlpha();
            otherLandmarkInSeconds = decayCounter.decay == null ? 0 : decayCounter.decay.getLandmarkInSeconds();
            otherCount = decayCounter.count;
        }

        checkArgument(otherAlpha == getAlpha(), "Expected decayCounter to have alpha %s, but was %s", getAlpha(), otherAlpha);

        synchronized (this) {
            if (decay == null) {
                // neither counter decays (equal alpha was checked above), so all weights are 1
                count += otherCount;
            }
            // if this counter's landmark is behind the other counter
            else if (decay.getLandmarkInSeconds() < otherLandmarkInSeconds) {
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
        if (decay != null) {
            decay.setLandmarkInSeconds(decay.nowInSeconds());
        }
        count = 0;
    }

    /**
     * This is a hack to work around limitations in Jmxutils.
     */
    @Deprecated
    public synchronized void resetTo(DecayCounter counter)
    {
        synchronized (counter) {
            if (decay != null && counter.decay != null) {
                decay.setLandmarkInSeconds(counter.decay.getLandmarkInSeconds());
            }
            count = counter.count;
        }
    }

    @Managed
    public synchronized double getCount()
    {
        if (decay == null) {
            return count;
        }
        return count / decay.currentWeight();
    }

    @Managed
    public synchronized double getRate()
    {
        // The total time covered by this counter is equivalent to the integral of the weight function from 0 to Infinity,
        // which equals 1/alpha. The count per unit time is, therefore, count / (1/alpha)
        return getCount() * getAlpha();
    }

    public DecayCounterSnapshot snapshot()
    {
        // synchronization on getCount() is sufficient
        double count = getCount();
        return new DecayCounterSnapshot(count, count * getAlpha());
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
        return decay == null ? 0.0 : decay.getAlpha();
    }

    public record DecayCounterSnapshot(double count, double rate) {}
}
