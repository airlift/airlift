package com.proofpoint.stats;

import com.google.common.base.Ticker;
import org.weakref.jmx.Managed;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

/*
 * A counter that decays exponentially. Values are weighted according to the formula
 *     w(t, α) = e^(-α * t), where α is the decay factor and t is the age in seconds
 *
 * The implementation is based on the ideas from
 * http://www.research.att.com/people/Cormode_Graham/library/publications/CormodeShkapenyukSrivastavaXu09.pdf
 * to not have to rely on a timer that decays the value periodically
 */
public final class DecayCounter
{
    // needs to be such that Math.exp(alpha * seconds) does not grow too big
    static final long RESCALE_THRESHOLD_SECONDS = 50;

    private final double alpha;
    private final Ticker ticker;

    private long landmarkInSeconds;
    private double count = 0.0;

    public DecayCounter(double alpha)
    {
        this(alpha, Ticker.systemTicker());
    }

    public DecayCounter(double alpha, Ticker ticker)
    {
        checkArgument(alpha > 0.0, "alpha is non-positive");
        this.alpha = alpha;
        this.ticker = ticker;
        landmarkInSeconds = getTickInSeconds();
    }

    public void add(long value)
    {
        add((double) value);
    }

    public synchronized void add(double value)
    {
        long nowInSeconds = getTickInSeconds();

        if (nowInSeconds - landmarkInSeconds >= RESCALE_THRESHOLD_SECONDS) {
            // rescale the count based on a new landmark to avoid numerical overflow issues
            count = count / weight(nowInSeconds, landmarkInSeconds);
            landmarkInSeconds = nowInSeconds;
        }
        count += value * weight(nowInSeconds, landmarkInSeconds);
    }

    @Managed
    public synchronized double getCount()
    {
        long nowInSeconds = getTickInSeconds();
        return count / weight(nowInSeconds, landmarkInSeconds);
    }

    @Managed
    public synchronized double getRate()
    {
        // The total time covered by this counter is equivalent to the integral of the weight function from 0 to Infinity,
        // which equals 1/alpha. The count per unit time is, therefore, count / (1/alpha)
        return getCount() * alpha;
    }


    private double weight(long timestampInSeconds, long landmarkInSeconds)
    {
        return Math.exp(alpha * (timestampInSeconds - landmarkInSeconds));
    }

    private long getTickInSeconds()
    {
        return TimeUnit.NANOSECONDS.toSeconds(ticker.read());
    }
}
