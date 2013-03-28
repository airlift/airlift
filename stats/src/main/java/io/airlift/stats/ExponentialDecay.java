package io.airlift.stats;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public final class ExponentialDecay
{
    private ExponentialDecay()
    {
    }

    public static double oneMinute()
    {
        // alpha for a target weight of 1/E at 1 minute
        return 1.0 / TimeUnit.MINUTES.toSeconds(1);
    }

    public static double fiveMinutes()
    {
        // alpha for a target weight of 1/E at 5 minutes
        return 1.0 / TimeUnit.MINUTES.toSeconds(5);
    }

    public static double fifteenMinutes()
    {
        // alpha for a target weight of 1/E at 15 minutes
        return 1.0 / TimeUnit.MINUTES.toSeconds(15);
    }

    public static double seconds(int seconds)
    {
        // alpha for a target weight of 1/E at the specified number of seconds
        return 1.0 / seconds;
    }

    /**
     * Compute the alpha decay factor such that the weight of an entry with age 'targetAgeInSeconds' is targetWeight'
     */
    public static double computeAlpha(double targetWeight, long targetAgeInSeconds)
    {
        checkArgument(targetAgeInSeconds > 0, "targetAgeInSeconds must be > 0");
        checkArgument(targetWeight > 0 && targetWeight < 1, "targetWeight must be in range (0, 1)");

        return -Math.log(targetWeight) / targetAgeInSeconds;
    }

}
