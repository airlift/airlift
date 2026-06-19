package io.airlift.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import jakarta.annotation.Nullable;
import org.weakref.jmx.Managed;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Ticker.systemTicker;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TimeDistribution
{
    @VisibleForTesting
    static final long MERGE_THRESHOLD_NANOS = MILLISECONDS.toNanos(100);

    private final TimeDistributionImplementation implementation;

    public TimeDistribution()
    {
        this(SECONDS);
    }

    public TimeDistribution(TimeUnit unit)
    {
        this(systemTicker(), unit);
    }

    public TimeDistribution(Ticker ticker)
    {
        this(ticker, SECONDS);
    }

    public TimeDistribution(Ticker ticker, TimeUnit unit)
    {
        this(ticker, (DecayConfig) null, unit);
    }

    public TimeDistribution(double alpha)
    {
        this(systemTicker(), alpha == 0.0 ? null : DecayConfig.of(alpha), SECONDS);
    }

    /**
     * @deprecated retained for backward compatibility; prefer {@link #TimeDistribution(Ticker, DecayConfig, TimeUnit)}.
     */
    @Deprecated
    public TimeDistribution(Ticker ticker, double alpha, TimeUnit unit)
    {
        this(ticker, alpha == 0.0 ? null : DecayConfig.of(alpha, ticker), unit);
    }

    /**
     * @param config the decay configuration, or null for a distribution that does not decay
     */
    public TimeDistribution(Ticker ticker, @Nullable DecayConfig config, TimeUnit unit)
    {
        requireNonNull(ticker, "ticker is null");
        requireNonNull(unit, "unit is null");
        implementation = switch (StatsBackendFactory.getBackend()) {
            case AIRLIFT -> new AirliftTimeDistribution(ticker, config, unit);
            case OPENTELEMETRY -> new OpenTelemetryTimeDistribution(ticker, unit);
        };
    }

    public void add(long value)
    {
        implementation.add(value);
    }

    @Managed
    public double getCount()
    {
        return implementation.getCount();
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
    public TimeUnit getUnit()
    {
        return implementation.getUnit();
    }

    @Managed
    public Map<Double, Double> getPercentiles()
    {
        return implementation.getPercentiles();
    }

    public TimeDistributionSnapshot snapshot()
    {
        return implementation.snapshot();
    }

    public Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot()
    {
        return implementation.exponentialHistogramSnapshot();
    }

    @Managed
    public void reset()
    {
        implementation.reset();
    }

    public record TimeDistributionSnapshot(
            double count,
            double p50,
            double p75,
            double p90,
            double p95,
            double p99,
            double min,
            double max,
            double avg,
            TimeUnit unit)
    {
        public TimeDistributionSnapshot
        {
            requireNonNull(unit, "unit is null");
        }
    }
}
