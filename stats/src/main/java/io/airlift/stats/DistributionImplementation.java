package io.airlift.stats;

import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import java.util.Map;
import java.util.Optional;

sealed interface DistributionImplementation
        permits AirliftDistribution, OpenTelemetryDistribution
{
    void add(long value);

    void add(long value, long count);

    DistributionImplementation duplicate();

    void reset();

    double getCount();

    double getTotal();

    double getP01();

    double getP05();

    double getP10();

    double getP25();

    double getP50();

    double getP75();

    double getP90();

    double getP95();

    double getP99();

    double getMin();

    double getMax();

    double getAvg();

    Map<Double, Double> getPercentiles();

    Distribution.DistributionSnapshot snapshot();

    Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot();
}
