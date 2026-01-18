package io.airlift.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.weakref.jmx.Managed;

import static com.google.common.base.Ticker.systemTicker;
import static java.lang.Math.clamp;
import static java.lang.Math.floorMod;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@ThreadSafe
public class Distribution
{
    @VisibleForTesting
    static final long MERGE_THRESHOLD_NANOS = MILLISECONDS.toNanos(100);

    private static final double[] SNAPSHOT_QUANTILES = new double[] {0.01, 0.05, 0.10, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};
    private static final double[] PERCENTILES;
    private static final int STRIPES = clamp(2, Runtime.getRuntime().availableProcessors(), 16);

    static {
        PERCENTILES = new double[100];
        for (int i = 0; i < 100; ++i) {
            PERCENTILES[i] = (i / 100.0);
        }
    }

    private final double alpha;
    private final Ticker ticker;

    private final Object[] locks = new Object[STRIPES];
    @GuardedBy("locks")
    private final DecayTDigest[] partials = new DecayTDigest[STRIPES];
    @GuardedBy("this")
    private DecayTDigest merged;
    @GuardedBy("this")
    private long lastMerge;
    private final DecayCounter total;
    private final DecayCounter partialTotal;

    public Distribution()
    {
        this(systemTicker(), 0);
    }

    public Distribution(double alpha)
    {
        this(systemTicker(), alpha);
    }

    Distribution(Ticker ticker, double alpha)
    {
        this(ticker, alpha, new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha), new DecayCounter(alpha));
    }

    private Distribution(Ticker ticker, double alpha, DecayTDigest merged, DecayCounter total)
    {
        requireNonNull(ticker, "ticker is null");
        this.alpha = alpha;
        this.merged = merged;
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
        }
        this.total = total;
        this.partialTotal = new DecayCounter(alpha);
        this.ticker = ticker;
        this.lastMerge = ticker.read(); // do not merge immediately
    }

    public void add(long value)
    {
        add(value, 1);
    }

    public void add(long value, long count)
    {
        int segment = floorMod(Thread.currentThread().threadId(), STRIPES);
        synchronized (locks[segment]) {
            if (partials[segment] == null) {
                partials[segment] = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
            }
            partials[segment].add(value, count);
        }
        partialTotal.add(value * count); // Fine outside of lock as DecayCounter is thread safe
    }

    @Managed
    public synchronized void reset()
    {
        total.reset();
        partialTotal.reset();
        merged = new DecayTDigest(TDigest.DEFAULT_COMPRESSION, alpha);
        // Reset all partial digests (stripes) to avoid stale data
        for (int i = 0; i < partials.length; i++) {
            synchronized (locks[i]) {
                partials[i] = null;
            }
        }
    }

    DecayTDigest mergeAndGetIfNeeded()
    {
        return mergeAndGetIfNeeded(false);
    }

    @VisibleForTesting
    @CanIgnoreReturnValue
    DecayTDigest mergeAndGetIfNeeded(boolean forceMerge)
    {
        synchronized (this) {
            if (forceMerge || ticker.read() - lastMerge >= MERGE_THRESHOLD_NANOS) {
                for (int i = 0; i < STRIPES; i++) {
                    synchronized (locks[i]) {
                        if (partials[i] == null) {
                            continue;
                        }
                        merged.merge(partials[i]);
                        // Reset the partial
                        partials[i] = null;
                    }
                }
                total.merge(partialTotal);
                partialTotal.reset();
                lastMerge = ticker.read();
            }
        }

        return merged;
    }

    @Managed
    public synchronized double getCount()
    {
        return mergeAndGetIfNeeded().getCount();
    }

    @Managed
    public double getTotal()
    {
        return total.getCount();
    }

    @Managed
    public double getP01()
    {
        return mergeAndGetIfNeeded().valueAt(0.01);
    }

    @Managed
    public double getP05()
    {
        return mergeAndGetIfNeeded().valueAt(0.05);
    }

    @Managed
    public double getP10()
    {
        return mergeAndGetIfNeeded().valueAt(0.10);
    }

    @Managed
    public double getP25()
    {
        return mergeAndGetIfNeeded().valueAt(0.25);
    }

    @Managed
    public double getP50()
    {
        return mergeAndGetIfNeeded().valueAt(0.5);
    }

    @Managed
    public double getP75()
    {
        return mergeAndGetIfNeeded().valueAt(0.75);
    }

    @Managed
    public double getP90()
    {
        return mergeAndGetIfNeeded().valueAt(0.90);
    }

    @Managed
    public double getP95()
    {
        return mergeAndGetIfNeeded().valueAt(0.95);
    }

    @Managed
    public double getP99()
    {
        return mergeAndGetIfNeeded().valueAt(0.99);
    }

    @Managed
    public double getMin()
    {
        return mergeAndGetIfNeeded().getMin();
    }

    @Managed
    public double getMax()
    {
        return mergeAndGetIfNeeded().getMax();
    }

    @Managed
    public double getAvg()
    {
        return getTotal() / getCount();
    }

    @Managed
    public double[] getPercentiles()
    {
        synchronized (this) {
            return mergeAndGetIfNeeded().valuesAt(PERCENTILES);
        }
    }

    public Distribution duplicate()
    {
        DecayTDigest digest = mergeAndGetIfNeeded(true);
        return new Distribution(ticker, alpha, digest.duplicate(), total.duplicate());
    }

    public DistributionSnapshot snapshot()
    {
        double totalCount;
        double digestCount;
        double min;
        double max;
        double[] quantiles;
        synchronized (this) {
            DecayTDigest digest = mergeAndGetIfNeeded(true);
            totalCount = total.getCount();
            digestCount = digest.getCount();
            min = digest.getMin();
            max = digest.getMax();
            quantiles = digest.valuesAt(SNAPSHOT_QUANTILES);
        }
        double average = totalCount / digestCount;
        return new DistributionSnapshot(
                digestCount,
                totalCount,
                quantiles[0], // p01
                quantiles[1], // p05
                quantiles[2], // p10
                quantiles[3], // p25
                quantiles[4], // p50
                quantiles[5], // p75
                quantiles[6], // p90
                quantiles[7], // p95
                quantiles[8], // p99
                min,
                max,
                average);
    }

    public record DistributionSnapshot(
            double count,
            double total,
            double p01,
            double p05,
            double p10,
            double p25,
            double p50,
            double p75,
            double p90,
            double p95,
            double p99,
            double min,
            double max,
            double avg)
    {
    }
}
