package io.airlift.opentelemetry;

import com.google.common.primitives.Longs;
import io.airlift.log.Logger;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramBuckets;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.stats.ExponentialHistogram.DEFAULT_MAX_BUCKETS;
import static io.airlift.stats.ExponentialHistogram.MIN_SCALE;
import static io.opentelemetry.sdk.metrics.InstrumentType.HISTOGRAM;
import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE;
import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.DELTA;
import static io.opentelemetry.sdk.metrics.data.MetricDataType.EXPONENTIAL_HISTOGRAM;
import static java.lang.Math.toIntExact;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MINUTES;

// MetricProducer data bypasses SDK aggregation, so the exporter's delta preference does not convert it.
// Each exporter needs its own history because multiple readers can collect from the same producer.
final class ExponentialHistogramDeltaExporter
        implements MetricExporter
{
    private static final Logger log = Logger.get(ExponentialHistogramDeltaExporter.class);

    private final MetricExporter delegate;
    private final Clock clock;
    private final long startEpochNanos;
    private final int maxTrackedSeries;
    private final int maxTrackedSeriesPerMetric;
    private final long maxStalenessNanos;
    private final LinkedHashMap<MetricIdentity, History> previousPoints = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<MetricFamily, Integer> familySizes = new HashMap<>();
    private long capacityDrops;
    private long lastWarningNanos;
    private boolean shutdown;

    ExponentialHistogramDeltaExporter(MetricExporter delegate, OpenTelemetryExporterConfig config)
    {
        this(delegate, config, Clock.getDefault());
    }

    ExponentialHistogramDeltaExporter(MetricExporter delegate, OpenTelemetryExporterConfig config, Clock clock)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
        this.clock = requireNonNull(clock, "clock is null");
        startEpochNanos = clock.now();
        maxTrackedSeries = config.getHistogramMaxTrackedSeries();
        maxTrackedSeriesPerMetric = config.getHistogramMaxTrackedSeriesPerMetric();
        maxStalenessNanos = config.getHistogramMaxStaleness().toJavaTime().toNanos();
        checkArgument(maxTrackedSeries > 0, "maxTrackedSeries must be positive");
        checkArgument(maxTrackedSeriesPerMetric > 0, "maxTrackedSeriesPerMetric must be positive");
        checkArgument(maxStalenessNanos > 0, "maxStaleness must be positive");
        lastWarningNanos = clock.nanoTime() - MINUTES.toNanos(1);
    }

    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType)
    {
        return delegate.getAggregationTemporality(instrumentType);
    }

    @Override
    public Aggregation getDefaultAggregation(InstrumentType instrumentType)
    {
        return delegate.getDefaultAggregation(instrumentType);
    }

    @Override
    public MemoryMode getMemoryMode()
    {
        return delegate.getMemoryMode();
    }

    @Override
    public synchronized CompletableResultCode export(Collection<MetricData> metrics)
    {
        if (shutdown) {
            return CompletableResultCode.ofFailure();
        }
        if (getAggregationTemporality(HISTOGRAM) != DELTA) {
            return delegate.export(metrics);
        }

        long now = clock.nanoTime();
        removeExpiredHistory(now);
        if (metrics.stream().noneMatch(ExponentialHistogramDeltaExporter::needsConversion)) {
            return delegate.export(metrics);
        }

        List<MetricData> convertedMetrics = new ArrayList<>(metrics.size());
        for (MetricData metric : metrics) {
            if (!needsConversion(metric)) {
                convertedMetrics.add(metric);
                continue;
            }

            MetricFamily family = new MetricFamily(metric.getResource(), metric.getInstrumentationScopeInfo(), metric.getName(), metric.getUnit());
            List<ExponentialHistogramPointData> points = new ArrayList<>();
            for (ExponentialHistogramPointData point : metric.getExponentialHistogramData().getPoints()) {
                MetricIdentity identity = new MetricIdentity(family, point.getAttributes());
                History previous = previousPoints.get(identity);
                if (previous == null && (previousPoints.size() >= maxTrackedSeries || familySizes.getOrDefault(family, 0) >= maxTrackedSeriesPerMetric)) {
                    capacityDrops++;
                    continue;
                }

                // Retained history needs histogram values, but exemplars belong only to the current export.
                ExponentialHistogramPointData snapshot = copyPoint(point);
                previousPoints.put(identity, new History(snapshot, now));
                if (previous == null) {
                    familySizes.merge(family, 1, Integer::sum);
                }
                if (previous == null || previous.point().getStartEpochNanos() != snapshot.getStartEpochNanos()) {
                    // Collector "auto" uses component startup, not another series' collection time.
                    // After history expiry this heuristic can accept an already-exported cumulative total.
                    if (snapshot.getStartEpochNanos() >= startEpochNanos && snapshot.getStartEpochNanos() != snapshot.getEpochNanos()) {
                        points.add(point.getExemplars().isEmpty() ? snapshot : restartedPoint(snapshot, snapshot.getStartEpochNanos(), point.getExemplars()));
                    }
                    continue;
                }
                points.add(toDelta(snapshot, previous.point(), point.getExemplars()));
            }
            if (points.isEmpty()) {
                continue;
            }
            convertedMetrics.add(ImmutableMetricData.createExponentialHistogram(
                    metric.getResource(),
                    metric.getInstrumentationScopeInfo(),
                    metric.getName(),
                    metric.getDescription(),
                    metric.getUnit(),
                    ExponentialHistogramData.create(DELTA, points)));
        }
        if (capacityDrops > 0 && now - lastWarningNanos >= MINUTES.toNanos(1)) {
            log.warn("Dropped %s cumulative histogram points because conversion history is full", capacityDrops);
            capacityDrops = 0;
            lastWarningNanos = now;
        }
        return delegate.export(convertedMetrics);
    }

    @Override
    public CompletableResultCode flush()
    {
        return delegate.flush();
    }

    @Override
    public synchronized CompletableResultCode shutdown()
    {
        shutdown = true;
        previousPoints.clear();
        familySizes.clear();
        return delegate.shutdown();
    }

    private void removeExpiredHistory(long now)
    {
        var iterator = previousPoints.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue().lastSeenNanos() < maxStalenessNanos) {
                return;
            }
            familySizes.compute(entry.getKey().family(), (_, size) -> size == 1 ? null : size - 1);
            iterator.remove();
        }
    }

    private static boolean needsConversion(MetricData metric)
    {
        return metric.getType() == EXPONENTIAL_HISTOGRAM && metric.getExponentialHistogramData().getAggregationTemporality() == CUMULATIVE;
    }

    private static ExponentialHistogramPointData copyPoint(ExponentialHistogramPointData point)
    {
        int scale = point.getScale();
        while (bucketRangeLength(point.getPositiveBuckets(), scale) > DEFAULT_MAX_BUCKETS || bucketRangeLength(point.getNegativeBuckets(), scale) > DEFAULT_MAX_BUCKETS) {
            checkArgument(scale > MIN_SCALE, "histogram bucket range exceeds limit at minimum scale");
            scale--;
        }
        return ExponentialHistogramPointData.create(
                scale,
                point.getSum(),
                point.getZeroCount(),
                point.hasMin(),
                point.getMin(),
                point.hasMax(),
                point.getMax(),
                copyBuckets(point.getPositiveBuckets(), scale),
                copyBuckets(point.getNegativeBuckets(), scale),
                point.getStartEpochNanos(),
                point.getEpochNanos(),
                point.getAttributes(),
                List.of());
    }

    private static long bucketRangeLength(ExponentialHistogramBuckets buckets, int scale)
    {
        if (buckets.getBucketCounts().isEmpty()) {
            return 0;
        }
        int shift = buckets.getScale() - scale;
        long last = ((long) buckets.getOffset() + buckets.getBucketCounts().size() - 1) >> shift;
        return last - (buckets.getOffset() >> shift) + 1;
    }

    private static ExponentialHistogramBuckets copyBuckets(ExponentialHistogramBuckets buckets, int scale)
    {
        long[] counts = new long[toIntExact(bucketRangeLength(buckets, scale))];
        int shift = buckets.getScale() - scale;
        int offset = buckets.getOffset() >> shift;
        List<Long> input = buckets.getBucketCounts();
        for (int index = 0; index < input.size(); index++) {
            int targetIndex = toIntExact((((long) buckets.getOffset() + index) >> shift) - offset);
            counts[targetIndex] += input.get(index);
        }
        return buckets(scale, offset, counts);
    }

    private static ExponentialHistogramPointData toDelta(ExponentialHistogramPointData point, ExponentialHistogramPointData previous, List<DoubleExemplarData> exemplars)
    {
        if (point.getCount() < previous.getCount() || point.getZeroCount() < previous.getZeroCount()) {
            return restartedPoint(point, previous.getEpochNanos(), exemplars);
        }

        int scale = Math.min(point.getScale(), previous.getScale());
        Optional<ExponentialHistogramBuckets> positive = subtractBuckets(point.getPositiveBuckets(), previous.getPositiveBuckets(), scale);
        Optional<ExponentialHistogramBuckets> negative = subtractBuckets(point.getNegativeBuckets(), previous.getNegativeBuckets(), scale);
        if (positive.isEmpty() || negative.isEmpty()) {
            return restartedPoint(point, previous.getEpochNanos(), exemplars);
        }

        // Cumulative extrema cannot be subtracted to recover the extrema of an interval.
        return ExponentialHistogramPointData.create(
                scale,
                point.getSum() - previous.getSum(),
                point.getZeroCount() - previous.getZeroCount(),
                false,
                0,
                false,
                0,
                positive.orElseThrow(),
                negative.orElseThrow(),
                previous.getEpochNanos(),
                point.getEpochNanos(),
                point.getAttributes(),
                List.copyOf(exemplars));
    }

    private static ExponentialHistogramPointData restartedPoint(ExponentialHistogramPointData point, long startEpochNanos, List<DoubleExemplarData> exemplars)
    {
        return ExponentialHistogramPointData.create(
                point.getScale(),
                point.getSum(),
                point.getZeroCount(),
                point.hasMin(),
                point.getMin(),
                point.hasMax(),
                point.getMax(),
                point.getPositiveBuckets(),
                point.getNegativeBuckets(),
                startEpochNanos,
                point.getEpochNanos(),
                point.getAttributes(),
                List.copyOf(exemplars));
    }

    private static Optional<ExponentialHistogramBuckets> subtractBuckets(ExponentialHistogramBuckets current, ExponentialHistogramBuckets previous, int scale)
    {
        int currentShift = current.getScale() - scale;
        long offset = current.getOffset() >> currentShift;
        long[] counts = new long[toIntExact(bucketRangeLength(current, scale))];
        List<Long> input = current.getBucketCounts();
        for (int index = 0; index < input.size(); index++) {
            counts[toIntExact((((long) current.getOffset() + index) >> currentShift) - offset)] += input.get(index);
        }
        int previousShift = previous.getScale() - scale;
        List<Long> previousCounts = previous.getBucketCounts();
        for (int index = 0; index < previousCounts.size(); index++) {
            long count = previousCounts.get(index);
            if (count == 0) {
                continue;
            }
            long targetIndex = (((long) previous.getOffset() + index) >> previousShift) - offset;
            if (targetIndex < 0 || targetIndex >= counts.length || counts[(int) targetIndex] < count) {
                return Optional.empty();
            }
            counts[(int) targetIndex] -= count;
        }
        return Optional.of(buckets(scale, toIntExact(offset), counts));
    }

    private static ExponentialHistogramBuckets buckets(int scale, int offset, long[] counts)
    {
        int first = 0;
        while (first < counts.length && counts[first] == 0) {
            first++;
        }
        if (first == counts.length) {
            return ExponentialHistogramBuckets.create(scale, 0, List.of());
        }
        int last = counts.length;
        while (counts[last - 1] == 0) {
            last--;
        }
        return ExponentialHistogramBuckets.create(scale, offset + first, unmodifiableList(Longs.asList(counts).subList(first, last)));
    }

    private record MetricFamily(Resource resource, InstrumentationScopeInfo scope, String name, String unit) {}

    private record MetricIdentity(MetricFamily family, Attributes attributes) {}

    private record History(ExponentialHistogramPointData point, long lastSeenNanos) {}
}
