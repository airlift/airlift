package io.airlift.stats;

import static java.util.Objects.requireNonNull;

/**
 * The mutable per-structure state of an exponential forward-decay timeline. It pairs an immutable
 * {@link DecayConfig} with:
 * <ul>
 *     <li>the {@code landmark} that the stored (forward-decayed) weights are relative to, and</li>
 *     <li>a one-entry cache of the most recently computed weight.</li>
 * </ul>
 * <p>
 * Because the landmark is mutable and the stored weights of the backing structure are anchored to
 * it, a single state must back exactly one data structure. A container that holds several decaying
 * structures should share one {@link DecayConfig} and give each structure its own state via
 * {@link DecayConfig#newState()}.
 * <p>
 * This class is NOT thread safe; callers that share an instance must synchronize externally.
 */
public final class DecayState
{
    private final DecayConfig config;
    private long landmarkInSeconds;

    // The weight is a pure function of the age (now - landmark) and alpha, so it is cached keyed by
    // age, maintaining the invariant cachedWeight == exp(alpha * cachedAgeInSeconds). A cache hit
    // (same age) is therefore always correct, even after the landmark moves, because equal ages yield
    // equal weights - so rescaleTo/setLandmarkInSeconds need not invalidate. A differing age
    // recomputes. Long.MIN_VALUE is an age no real (now - landmark) produces, marking "nothing cached".
    private double cachedWeight;
    private long cachedAgeInSeconds = Long.MIN_VALUE;

    DecayState(DecayConfig config, long landmarkInSeconds)
    {
        this.config = requireNonNull(config, "config is null");
        this.landmarkInSeconds = landmarkInSeconds;
    }

    public DecayConfig config()
    {
        return config;
    }

    public double getAlpha()
    {
        return config.getAlpha();
    }

    public boolean isDecaying()
    {
        return config.isDecaying();
    }

    public long getLandmarkInSeconds()
    {
        return landmarkInSeconds;
    }

    public long nowInSeconds()
    {
        return config.nowInSeconds();
    }

    /**
     * Whether enough time has elapsed since the landmark that the weights should be rescaled to a
     * fresh landmark to avoid numerical overflow.
     */
    public boolean needsRescale(long nowInSeconds)
    {
        return config.needsRescale(nowInSeconds - landmarkInSeconds);
    }

    /**
     * The decay multiplier at the given time relative to the current landmark. The result is cached
     * by age (time minus landmark) so repeated reads at the same age do not recompute {@link Math#exp}.
     */
    public double weightAt(long nowInSeconds)
    {
        if (!config.isDecaying()) {
            return 1.0;
        }
        long ageInSeconds = nowInSeconds - landmarkInSeconds;
        if (ageInSeconds != cachedAgeInSeconds) {
            cachedAgeInSeconds = ageInSeconds;
            cachedWeight = config.weightForAge(ageInSeconds);
        }
        return cachedWeight;
    }

    /**
     * The decay multiplier at the current time (read from the ticker) relative to the landmark.
     */
    public double currentWeight()
    {
        return weightAt(nowInSeconds());
    }

    /**
     * The decay multiplier of this state's landmark relative to an older landmark. Used to align two
     * timelines without mutating either one.
     */
    public double weightFromLandmark(long otherLandmarkInSeconds)
    {
        return config.weight(landmarkInSeconds, otherLandmarkInSeconds);
    }

    /**
     * Move the landmark forward, returning the factor by which already-stored weights must be
     * divided to remain relative to the new landmark.
     */
    public double rescaleTo(long newLandmarkInSeconds)
    {
        double factor = config.weight(newLandmarkInSeconds, landmarkInSeconds);
        landmarkInSeconds = newLandmarkInSeconds;
        return factor;
    }

    /**
     * Reset the landmark without rescaling. Callers are responsible for resetting any weights that
     * were relative to the previous landmark.
     */
    public void setLandmarkInSeconds(long landmarkInSeconds)
    {
        this.landmarkInSeconds = landmarkInSeconds;
    }

    /**
     * An independent copy with the same config and current landmark.
     */
    public DecayState copy()
    {
        return new DecayState(config, landmarkInSeconds);
    }
}
