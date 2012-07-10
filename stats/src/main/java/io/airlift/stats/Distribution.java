package io.airlift.stats;

import org.weakref.jmx.Managed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Distribution
{
    private final static double MAX_ERROR = 0.01;

    private final QuantileDigest digest;

    public Distribution()
    {
        digest = new QuantileDigest(MAX_ERROR);
    }

    public Distribution(double alpha)
    {
        digest = new QuantileDigest(MAX_ERROR, alpha);
    }

    public void add(long value)
    {
        digest.add(value);
    }

    @Managed
    public double getMaxError()
    {
        return digest.getConfidenceFactor();
    }

    @Managed
    public double getCount()
    {
        return digest.getCount();
    }

    @Managed
    public long getP50()
    {
        return digest.getQuantile(0.5);
    }

    @Managed
    public long getP75()
    {
        return digest.getQuantile(0.75);
    }

    @Managed
    public long getP90()
    {
        return digest.getQuantile(0.90);
    }

    @Managed
    public long getP95()
    {
        return digest.getQuantile(0.95);
    }

    @Managed
    public long getP99()
    {
        return digest.getQuantile(0.99);
    }

    @Managed
    public long getMin()
    {
        return digest.getMin();
    }

    @Managed
    public long getMax()
    {
        return digest.getMax();
    }

    @Managed
    public Map<Double, Long> getPercentiles()
    {
        List<Double> percentiles = new ArrayList<Double>(100);
        for (int i = 0; i < 100; ++i) {
            percentiles.add(i / 100.0);
        }

        List<Long> values = digest.getQuantiles(percentiles);

        Map<Double, Long> result = new LinkedHashMap<Double, Long>(values.size());
        for (int i = 0; i < percentiles.size(); ++i) {
            result.put(percentiles.get(i), values.get(i));
        }

        return result;
    }
}
