package io.airlift.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.errorprone.annotations.ThreadSafe;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import jakarta.annotation.Nullable;
import org.weakref.jmx.Managed;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Ticker.systemTicker;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@ThreadSafe
public class Distribution
{
    @VisibleForTesting
    static final long MERGE_THRESHOLD_NANOS = MILLISECONDS.toNanos(100);

    private final DistributionImplementation implementation;

    public Distribution()
    {
        this((DecayConfig) null);
    }

    public Distribution(double alpha)
    {
        this(alpha == 0.0 ? null : DecayConfig.of(alpha));
    }

    /**
     * @param config the decay configuration, or null for a distribution that does not decay
     */
    public Distribution(@Nullable DecayConfig config)
    {
        this(systemTicker(), config);
    }

    @VisibleForTesting
    Distribution(Ticker ticker)
    {
        this(ticker, null);
    }

    private Distribution(Ticker ticker, @Nullable DecayConfig config)
    {
        implementation = switch (StatsBackendFactory.getBackend()) {
            case AIRLIFT -> new AirliftDistribution(config);
            case OPENTELEMETRY -> new OpenTelemetryDistribution(ticker);
        };
    }

    private Distribution(DistributionImplementation implementation)
    {
        this.implementation = requireNonNull(implementation, "implementation is null");
    }

    public void add(long value)
    {
        implementation.add(value);
    }

    public void add(long value, long count)
    {
        implementation.add(value, count);
    }

    public Distribution duplicate()
    {
        return new Distribution(implementation.duplicate());
    }

    @Managed
    public void reset()
    {
        implementation.reset();
    }

    @Managed
    public double getCount()
    {
        return implementation.getCount();
    }

    @Managed
    public double getTotal()
    {
        return implementation.getTotal();
    }

    @Managed
    public double getP01()
    {
        return implementation.getP01();
    }

    @Managed
    public double getP05()
    {
        return implementation.getP05();
    }

    @Managed
    public double getP10()
    {
        return implementation.getP10();
    }

    @Managed
    public double getP25()
    {
        return implementation.getP25();
    }

    @Managed
    public double getP50()
    {
        return implementation.getP50();
    }

    @Managed
    public double getP75()
    {
        return implementation.getP75();
    }

    @Managed
    public double getP90()
    {
        return implementation.getP90();
    }

    @Managed
    public double getP95()
    {
        return implementation.getP95();
    }

    @Managed
    public double getP99()
    {
        return implementation.getP99();
    }

    @Managed
    public double getMin()
    {
        return implementation.getMin();
    }

    @Managed
    public double getMax()
    {
        return implementation.getMax();
    }

    @Managed
    public double getAvg()
    {
        return implementation.getAvg();
    }

    @Managed
    public Map<Double, Double> getPercentiles()
    {
        return implementation.getPercentiles();
    }

    public DistributionSnapshot snapshot()
    {
        return implementation.snapshot();
    }

    public Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot()
    {
        return implementation.exponentialHistogramSnapshot();
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
            double avg) {}
}
