package io.airlift.http.client.jetty;

import io.airlift.stats.Distribution;
import org.weakref.jmx.Managed;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.util.Map;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/*
 * This class is needed because jmxutils only fetches a nested instance object once and holds on to it forever.
 * todo remove this when https://github.com/martint/jmxutils/issues/26 is implemented
 */
@ThreadSafe
class CachedDistribution
{
    private final Supplier<Distribution> distributionSupplier;

    @GuardedBy("this")
    private Distribution distribution;
    @GuardedBy("this")
    private long lastUpdate = System.nanoTime();

    public CachedDistribution(Supplier<Distribution> distributionSupplier)
    {
        this.distributionSupplier = distributionSupplier;
    }

    public synchronized Distribution getDistribution()
    {
        // refresh stats only once a second
        if (NANOSECONDS.toMillis(System.nanoTime() - lastUpdate) > 1000) {
            this.distribution = distributionSupplier.get();
            this.lastUpdate = System.nanoTime();
        }
        return distribution;
    }

    @Managed
    public double getMaxError()
    {
        return getDistribution().getMaxError();
    }

    @Managed
    public double getCount()
    {
        return getDistribution().getCount();
    }

    @Managed
    public double getTotal()
    {
        return getDistribution().getTotal();
    }

    @Managed
    public long getP01()
    {
        return getDistribution().getP01();
    }

    @Managed
    public long getP05()
    {
        return getDistribution().getP05();
    }

    @Managed
    public long getP10()
    {
        return getDistribution().getP10();
    }

    @Managed
    public long getP25()
    {
        return getDistribution().getP25();
    }

    @Managed
    public long getP50()
    {
        return getDistribution().getP50();
    }

    @Managed
    public long getP75()
    {
        return getDistribution().getP75();
    }

    @Managed
    public long getP90()
    {
        return getDistribution().getP90();
    }

    @Managed
    public long getP95()
    {
        return getDistribution().getP95();
    }

    @Managed
    public long getP99()
    {
        return getDistribution().getP99();
    }

    @Managed
    public long getMin()
    {
        return getDistribution().getMin();
    }

    @Managed
    public long getMax()
    {
        return getDistribution().getMax();
    }

    @Managed
    public Map<Double, Long> getPercentiles()
    {
        return getDistribution().getPercentiles();
    }
}
