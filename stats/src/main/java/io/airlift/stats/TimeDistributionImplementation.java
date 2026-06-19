package io.airlift.stats;

import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

sealed interface TimeDistributionImplementation
        permits AirliftTimeDistribution, OpenTelemetryTimeDistribution
{
    void add(long value);

    double getCount();

    double getP50();

    double getP75();

    double getP90();

    double getP95();

    double getP99();

    double getMin();

    double getMax();

    double getAvg();

    TimeUnit getUnit();

    Map<Double, Double> getPercentiles();

    TimeDistribution.TimeDistributionSnapshot snapshot();

    Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot();

    void reset();
}
