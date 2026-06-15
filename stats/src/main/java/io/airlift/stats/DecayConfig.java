package io.airlift.stats;

import com.google.common.base.Ticker;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Ticker.systemTicker;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Immutable configuration for an exponential forward-decay timeline. It holds the two pieces of
 * state that never change for the lifetime of a decaying data structure:
 * <ul>
 *     <li>the decay factor {@code alpha}, and</li>
 *     <li>the {@link Ticker} used to measure the age of entries.</li>
 * </ul>
 * <p>
 * Weights follow the formula {@code w(t, alpha) = e^(alpha * (t - landmark))}, based on the ideas in
 * <a href="http://dimacs.rutgers.edu/~graham/pubs/papers/fwddecay.pdf">Forward Decay</a>. The
 * mutable part of a timeline (the landmark, plus a cached weight) lives in {@link DecayState}.
 * <p>
 * A config carries no mutable state, so a single instance is safe to share across any number of
 * decaying data structures and threads. Each structure derives its own mutable {@link DecayState}
 * via {@link #newState()}.
 */
public record DecayConfig(double alpha, Ticker ticker)
{
    // needs to be such that Math.exp(alpha * seconds) does not grow too big
    static final long RESCALE_THRESHOLD_SECONDS = 50;

    public DecayConfig
    {
        // alpha == 0 (no decay) is not a configuration of a decay timeline; callers that don't want
        // decay simply don't hold a DecayConfig/DecayState at all
        checkArgument(alpha > 0 && alpha < 1, "alpha must be in range (0, 1)");
        requireNonNull(ticker, "ticker is null");
    }

    public static DecayConfig oneMinute()
    {
        // alpha for a target weight of 1/E at 1 minute
        return seconds(60);
    }

    public static DecayConfig oneMinute(Ticker ticker)
    {
        return seconds(60, ticker);
    }

    public static DecayConfig fiveMinutes()
    {
        // alpha for a target weight of 1/E at 5 minutes
        return seconds(300);
    }

    public static DecayConfig fiveMinutes(Ticker ticker)
    {
        return seconds(300, ticker);
    }

    public static DecayConfig fifteenMinutes()
    {
        // alpha for a target weight of 1/E at 15 minutes
        return seconds(900);
    }

    public static DecayConfig fifteenMinutes(Ticker ticker)
    {
        return seconds(900, ticker);
    }

    public static DecayConfig seconds(int seconds)
    {
        return seconds(seconds, systemTicker());
    }

    public static DecayConfig seconds(int seconds, Ticker ticker)
    {
        // alpha for a target weight of 1/E at the specified number of seconds; the derived alpha
        // (1 / seconds) must land in [0, 1), so seconds must be greater than 1
        checkArgument(seconds > 1, "seconds must be greater than 1");
        return new DecayConfig(1.0 / seconds, ticker);
    }

    /**
     * A config with the given decay factor, bound to the system ticker.
     */
    public static DecayConfig of(double alpha)
    {
        return new DecayConfig(alpha, systemTicker());
    }

    /**
     * A config with the given decay factor, bound to the given ticker.
     */
    public static DecayConfig of(double alpha, Ticker ticker)
    {
        return new DecayConfig(alpha, ticker);
    }

    /**
     * Compute the alpha decay factor such that the weight of an entry with age 'targetAgeInSeconds' is 'targetWeight'
     */
    public static double computeAlpha(double targetWeight, long targetAgeInSeconds)
    {
        checkArgument(targetAgeInSeconds > 0, "targetAgeInSeconds must be > 0");
        checkArgument(targetWeight > 0 && targetWeight < 1, "targetWeight must be in range (0, 1)");

        return -Math.log(targetWeight) / targetAgeInSeconds;
    }

    public long nowInSeconds()
    {
        return NANOSECONDS.toSeconds(ticker.read());
    }

    /**
     * A fresh, independent mutable state for this config, whose landmark starts at "now". Use when a
     * data structure (or one sub-structure of a container) needs its own decay timeline.
     */
    public DecayState newState()
    {
        return new DecayState(this, nowInSeconds());
    }

    /**
     * A mutable state for this config anchored at a specific landmark. Used to reconstruct a timeline
     * from previously captured state (e.g. after deserialization), where stored weights are relative
     * to that landmark.
     */
    public DecayState newState(long landmarkInSeconds)
    {
        return new DecayState(this, landmarkInSeconds);
    }

    /**
     * Whether an entry of the given age (now minus landmark) is old enough that the weights should be
     * rescaled to a fresh landmark to avoid numerical overflow.
     */
    boolean needsRescale(long ageInSeconds)
    {
        return ageInSeconds >= RESCALE_THRESHOLD_SECONDS;
    }

    /**
     * The forward-decay multiplier for an entry of the given age.
     */
    double weightForAge(long ageInSeconds)
    {
        return Math.exp(alpha * ageInSeconds);
    }

    /**
     * The forward-decay multiplier at time {@code now} relative to {@code landmark}.
     */
    double weight(long now, long landmark)
    {
        return weightForAge(now - landmark);
    }
}
