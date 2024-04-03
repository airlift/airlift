package io.airlift.stats;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Ticker;
import com.google.errorprone.annotations.ThreadSafe;
import org.weakref.jmx.Managed;

import java.util.concurrent.TimeUnit;

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

    private long landmarkInSeconds;
    private double count;

    public DecayCounter(double alpha)
    {
        this(alpha, Ticker.systemTicker());
    }

    public DecayCounter(double alpha, Ticker ticker)
    {
        this(0, alpha, ticker, TimeUnit.NANOSECONDS.toSeconds(ticker.read()));
    }

    private DecayCounter(double count, double alpha, Ticker ticker, long landmarkInSeconds)
    {
        this.count = count;
        this.alpha = alpha;
        this.ticker = ticker;
        this.landmarkInSeconds = landmarkInSeconds;
    }

    public DecayCounter duplicate()
    {
        return new DecayCounter(count, alpha, ticker, landmarkInSeconds);
    }

    public synchronized void add(long value)
    {
        long nowInSeconds = getTickInSeconds();

        if (nowInSeconds - landmarkInSeconds >= RESCALE_THRESHOLD_SECONDS) {
            rescaleToNewLandmark(nowInSeconds);
        }
        count += value * weight(alpha, nowInSeconds, landmarkInSeconds);
    }

    public synchronized void merge(DecayCounter decayCounter)
    {
        requireNonNull(decayCounter, "decayCounter is null");
        checkArgument(decayCounter.alpha == alpha, "Expected decayCounter to have alpha %s, but was %s", alpha, decayCounter.alpha);

        synchronized (decayCounter) {
            // if the landmark this counter is behind the other counter
            if (landmarkInSeconds < decayCounter.landmarkInSeconds) {
                // rescale this counter to the other counter, and add
                rescaleToNewLandmark(decayCounter.landmarkInSeconds);
                count += decayCounter.count;
            }
            else {
                // rescale the other counter and add
                double otherRescaledCount = decayCounter.count / weight(alpha, landmarkInSeconds, decayCounter.landmarkInSeconds);
                count += otherRescaledCount;
            }
        }
    }

    private void rescaleToNewLandmark(long newLandMarkInSeconds)
    {
        // rescale the count based on a new landmark to avoid numerical overflow issues
        count = count / weight(alpha, newLandMarkInSeconds, landmarkInSeconds);
        landmarkInSeconds = newLandMarkInSeconds;
    }

    @Managed
    public synchronized void reset()
    {
        landmarkInSeconds = getTickInSeconds();
        count = 0;
    }

    /**
     * This is a hack to work around limitations in Jmxutils.
     */
    @Deprecated
    public synchronized void resetTo(DecayCounter counter)
    {
        synchronized (counter) {
            landmarkInSeconds = counter.landmarkInSeconds;
            count = counter.count;
        }
    }

    @Managed
    public synchronized double getCount()
    {
        long nowInSeconds = getTickInSeconds();
        return count / weight(alpha, nowInSeconds, landmarkInSeconds);
    }

    @Managed
    public synchronized double getRate()
    {
        // The total time covered by this counter is equivalent to the integral of the weight function from 0 to Infinity,
        // which equals 1/alpha. The count per unit time is, therefore, count / (1/alpha)
        return getCount() * alpha;
    }

    private long getTickInSeconds()
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

    public record DecayCounterSnapshot(@JsonProperty("count") double count, @JsonProperty("rate")double rate)
    {
    }
}
