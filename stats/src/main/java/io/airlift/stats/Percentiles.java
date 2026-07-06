package io.airlift.stats;

import java.util.LinkedHashMap;
import java.util.Map;

final class Percentiles
{
    static final double[] PERCENTILES;

    static {
        PERCENTILES = new double[100];
        for (int i = 0; i < 100; ++i) {
            PERCENTILES[i] = (i / 100.0);
        }
    }

    private Percentiles() {}

    static Map<Double, Double> toMap(double[] values)
    {
        Map<Double, Double> result = new LinkedHashMap<>(values.length);
        for (int i = 0; i < values.length; ++i) {
            result.put(PERCENTILES[i], values[i]);
        }
        return result;
    }
}
