package io.airlift.stats;

import com.google.common.base.Ticker;
import org.weakref.jmx.Managed;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Ticker.systemTicker;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TimeDistribution
{
    static final long MERGE_THRESHOLD_NANOS = MILLISECONDS.toNanos(100);
    private final Distribution distribution;
    private final TimeUnit unit;

    public TimeDistribution()
    {
        this(SECONDS);
    }

    public TimeDistribution(TimeUnit unit)
    {
        this(systemTicker(), 0, unit);
    }

    public TimeDistribution(Ticker ticker)
    {
        this(ticker, 0, SECONDS);
    }

    public TimeDistribution(double alpha)
    {
        this(systemTicker(), alpha, SECONDS);
    }

    public TimeDistribution(Ticker ticker, double alpha, TimeUnit unit)
    {
        this(new Distribution(ticker, alpha), unit);
    }

    private TimeDistribution(Distribution distribution, TimeUnit unit)
    {
        this.distribution = requireNonNull(distribution, "distribution is null");
        this.unit = requireNonNull(unit, "unit is null");
    }

    public void addNanos(long value)
    {
        distribution.add(value);
    }

    @Managed
    public double getCount()
    {
        return distribution.getCount();
    }

    public double getTotal()
    {
        return distribution.getTotal();
    }

    @Managed
    public double getP50()
    {
        return convertToUnit(distribution.getP50());
    }

    @Managed
    public double getP75()
    {
        return convertToUnit(distribution.getP75());
    }

    @Managed
    public double getP90()
    {
        return convertToUnit(distribution.getP90());
    }

    @Managed
    public double getP95()
    {
        return convertToUnit(distribution.getP95());
    }

    @Managed
    public double getP99()
    {
        return convertToUnit(distribution.getP99());
    }

    @Managed
    public double getMin()
    {
        return convertToUnit(distribution.getMin());
    }

    @Managed
    public double getMax()
    {
        return convertToUnit(distribution.getMax());
    }

    @Managed
    public synchronized double getAvg()
    {
        double digestCount = distribution.getCount();
        return convertToUnit(distribution.getTotal()) / digestCount;
    }

    @Managed
    public TimeUnit getUnit()
    {
        return unit;
    }

    @Managed
    public double[] getPercentiles()
    {
        double[] values = distribution.getPercentiles();
        for (int i = 0; i < values.length; ++i) {
            values[i] = convertToUnit(values[i]);
        }
        return values;
    }

    private double convertToUnit(double nanos)
    {
        return convertToUnit(nanos, (double) unit.toNanos(1));
    }

    private static double convertToUnit(double nanos, double unitNanos)
    {
        return nanos / unitNanos;
    }

    public TimeDistribution duplicate()
    {
        return new TimeDistribution(distribution.duplicate(), unit);
    }

    public TimeDistributionSnapshot snapshot()
    {
        Distribution.DistributionSnapshot snapshot = distribution.snapshot();
        double unitNanos = (double) unit.toNanos(1);
        double average = convertToUnit(snapshot.total(), unitNanos) / snapshot.count();

        return new TimeDistributionSnapshot(
                snapshot.count(),
                convertToUnit(snapshot.p50(), unitNanos), // p50
                convertToUnit(snapshot.p75(), unitNanos), // p75
                convertToUnit(snapshot.p90(), unitNanos), // p90
                convertToUnit(snapshot.p95(), unitNanos), // p95
                convertToUnit(snapshot.p99(), unitNanos), // p99
                convertToUnit(snapshot.min(), unitNanos),
                convertToUnit(snapshot.max(), unitNanos),
                average,
                unit);
    }

    @Managed
    public synchronized void reset()
    {
        distribution.reset();
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
