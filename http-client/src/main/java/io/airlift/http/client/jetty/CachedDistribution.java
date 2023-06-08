package io.airlift.http.client.jetty;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.stats.Distribution;
import org.weakref.jmx.Managed;

import java.util.Map;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/*
 * This class is needed because jmxutils only fetches a nested instance object once and holds on to it forever.
 * todo remove this when https://github.com/martint/jmxutils/issues/26 is implemented
 */
@ThreadSafe
public class CachedDistribution
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
    public double getP01()
    {
        return getDistribution().getP01();
    }

    @Managed
    public double getP05()
    {
        return getDistribution().getP05();
    }

    @Managed
    public double getP10()
    {
        return getDistribution().getP10();
    }

    @Managed
    public double getP25()
    {
        return getDistribution().getP25();
    }

    @Managed
    public double getP50()
    {
        return getDistribution().getP50();
    }

    @Managed
    public double getP75()
    {
        return getDistribution().getP75();
    }

    @Managed
    public double getP90()
    {
        return getDistribution().getP90();
    }

    @Managed
    public double getP95()
    {
        return getDistribution().getP95();
    }

    @Managed
    public double getP99()
    {
        return getDistribution().getP99();
    }

    @Managed
    public double getMin()
    {
        return getDistribution().getMin();
    }

    @Managed
    public double getMax()
    {
        return getDistribution().getMax();
    }

    @Managed
    public Map<Double, Double> getPercentiles()
    {
        return getDistribution().getPercentiles();
    }
}
