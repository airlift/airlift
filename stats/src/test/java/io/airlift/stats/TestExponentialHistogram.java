package io.airlift.stats;

import io.airlift.stats.ExponentialHistogram.Buckets;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class TestExponentialHistogram
{
    @Test
    public void testEmptySnapshot()
    {
        ExponentialHistogramSnapshot snapshot = new ExponentialHistogram(0, 10).snapshot();

        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.count()).isEqualTo(0);
        assertThat(snapshot.sum()).isEqualTo(0);
        assertThat(snapshot.min()).isNaN();
        assertThat(snapshot.max()).isNaN();
        assertThat(snapshot.zeroCount()).isEqualTo(0);
        assertThat(snapshot.positiveBuckets().isEmpty()).isTrue();
        assertThat(snapshot.negativeBuckets().isEmpty()).isTrue();
    }

    @Test
    public void testRecordsPositiveNegativeAndZeroValues()
    {
        ExponentialHistogram histogram = new ExponentialHistogram(0, 10);

        histogram.record(0);
        histogram.record(1);
        histogram.record(1.5);
        histogram.record(2);
        histogram.record(3);
        histogram.record(-1);
        histogram.record(-2);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();

        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.count()).isEqualTo(7);
        assertThat(snapshot.sum()).isEqualTo(4.5);
        assertThat(snapshot.min()).isEqualTo(-2);
        assertThat(snapshot.max()).isEqualTo(3);
        assertThat(snapshot.zeroCount()).isEqualTo(1);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 2, 1}));
        assertThat(snapshot.negativeBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 1}));
    }

    @Test
    public void testRecordsWeightedValues()
    {
        ExponentialHistogram histogram = new ExponentialHistogram(0, 10);

        histogram.record(2, 3);
        histogram.record(0, 4);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();

        assertThat(snapshot.count()).isEqualTo(7);
        assertThat(snapshot.sum()).isEqualTo(6);
        assertThat(snapshot.min()).isEqualTo(0);
        assertThat(snapshot.max()).isEqualTo(2);
        assertThat(snapshot.zeroCount()).isEqualTo(4);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(0, new long[] {3}));
    }

    @Test
    public void testDownscalesToFitBucketLimit()
    {
        ExponentialHistogram histogram = new ExponentialHistogram(1, 3);

        histogram.record(1);
        histogram.record(2);
        histogram.record(4);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();

        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.count()).isEqualTo(3);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 1, 1}));
    }

    @Test
    public void testDownscalesExtremeRange()
    {
        ExponentialHistogram histogram = new ExponentialHistogram();

        histogram.record(Double.MIN_VALUE);
        histogram.record(Double.MAX_VALUE);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();

        assertThat(snapshot.count()).isEqualTo(2);
        assertThat(snapshot.scale()).isLessThan(ExponentialHistogram.DEFAULT_SCALE);
        assertThat(snapshot.positiveBuckets().counts()).hasSizeLessThanOrEqualTo(ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        assertThat(snapshot.positiveBuckets().counts()).contains(1, 1);
    }

    @Test
    public void testSnapshotIsImmutable()
    {
        ExponentialHistogram histogram = new ExponentialHistogram(0, 10);
        histogram.record(1);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();
        histogram.record(2);

        assertThat(snapshot.count()).isEqualTo(1);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {1}));
    }

    @Test
    public void testMergeEmptySnapshots()
    {
        ExponentialHistogramSnapshot snapshot = ExponentialHistogramSnapshot.merge(List.of());

        assertThat(snapshot.scale()).isEqualTo(ExponentialHistogram.DEFAULT_SCALE);
        assertThat(snapshot.count()).isEqualTo(0);
        assertThat(snapshot.sum()).isEqualTo(0);
        assertThat(snapshot.min()).isNaN();
        assertThat(snapshot.max()).isNaN();
        assertThat(snapshot.zeroCount()).isEqualTo(0);
        assertThat(snapshot.positiveBuckets().isEmpty()).isTrue();
        assertThat(snapshot.negativeBuckets().isEmpty()).isTrue();
    }

    @Test
    public void testMergeZeroOnlySnapshots()
    {
        ExponentialHistogram first = new ExponentialHistogram(5, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        ExponentialHistogram second = new ExponentialHistogram(3, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        first.record(0);
        second.record(0);

        ExponentialHistogramSnapshot snapshot = ExponentialHistogramSnapshot.merge(List.of(first.snapshot(), second.snapshot()));

        assertThat(snapshot.scale()).isEqualTo(3);
        assertThat(snapshot.count()).isEqualTo(2);
        assertThat(snapshot.sum()).isEqualTo(0);
        assertThat(snapshot.min()).isEqualTo(0);
        assertThat(snapshot.max()).isEqualTo(0);
        assertThat(snapshot.zeroCount()).isEqualTo(2);
        assertThat(snapshot.positiveBuckets().isEmpty()).isTrue();
        assertThat(snapshot.negativeBuckets().isEmpty()).isTrue();
    }

    @Test
    public void testMergeSameScaleSnapshots()
    {
        ExponentialHistogram first = new ExponentialHistogram(0, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        ExponentialHistogram second = new ExponentialHistogram(0, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        first.record(1);
        first.record(2);
        first.record(-1);
        second.record(2);
        second.record(4);
        second.record(-2);

        ExponentialHistogramSnapshot snapshot = ExponentialHistogramSnapshot.merge(List.of(first.snapshot(), second.snapshot()));

        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.count()).isEqualTo(6);
        assertThat(snapshot.sum()).isEqualTo(6);
        assertThat(snapshot.min()).isEqualTo(-2);
        assertThat(snapshot.max()).isEqualTo(4);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 2, 1}));
        assertThat(snapshot.negativeBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 1}));
    }

    @Test
    public void testMergeDifferentScaleSnapshots()
    {
        ExponentialHistogram first = new ExponentialHistogram(1, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        ExponentialHistogram second = new ExponentialHistogram(0, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        first.record(1);
        first.record(2);
        second.record(4);

        ExponentialHistogramSnapshot snapshot = ExponentialHistogramSnapshot.merge(List.of(first.snapshot(), second.snapshot()));

        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.count()).isEqualTo(3);
        assertThat(snapshot.sum()).isEqualTo(7);
        assertThat(snapshot.min()).isEqualTo(1);
        assertThat(snapshot.max()).isEqualTo(4);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 1, 1}));
    }

    @Test
    public void testMergeDownscalesToFitBucketLimit()
    {
        ExponentialHistogram first = new ExponentialHistogram(1, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        ExponentialHistogram second = new ExponentialHistogram(1, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        first.record(1);
        second.record(4);

        ExponentialHistogramSnapshot snapshot = ExponentialHistogramSnapshot.merge(List.of(first.snapshot(), second.snapshot()), 3);

        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.count()).isEqualTo(2);
        assertThat(snapshot.sum()).isEqualTo(5);
        assertThat(snapshot.min()).isEqualTo(1);
        assertThat(snapshot.max()).isEqualTo(4);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 0, 1}));
    }

    @Test
    public void testMergeRejectsSnapshotRangeThatCannotFitAtMinimumScale()
    {
        assertThatThrownBy(() -> new ExponentialHistogramSnapshot(
                ExponentialHistogram.MIN_SCALE,
                4,
                0,
                0,
                0,
                0,
                new Buckets(0, new long[] {1, 1, 1, 1}),
                new Buckets(0, new long[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bucket range exceeds finite value range for scale -10");
    }

    @Test
    public void testSnapshotFiniteBucketRangeBoundariesAtEveryScale()
    {
        for (int scale = ExponentialHistogram.MIN_SCALE; scale <= ExponentialHistogram.MAX_SCALE; scale++) {
            int testedScale = scale;
            int minimumBucketIndex = bucketIndexForValue(testedScale, Double.MIN_VALUE);
            int maximumBucketIndex = bucketIndexForValue(testedScale, Double.MAX_VALUE);

            assertThatCode(() -> snapshotWithPositiveBucket(testedScale, minimumBucketIndex))
                    .describedAs("minimum bucket index at scale %s", testedScale)
                    .doesNotThrowAnyException();
            assertThatCode(() -> snapshotWithPositiveBucket(testedScale, maximumBucketIndex))
                    .describedAs("maximum bucket index at scale %s", testedScale)
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> snapshotWithPositiveBucket(testedScale, minimumBucketIndex - 1))
                    .describedAs("below minimum bucket index at scale %s", testedScale)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("bucket range exceeds finite value range for scale %s", testedScale);
            assertThatThrownBy(() -> snapshotWithPositiveBucket(testedScale, maximumBucketIndex + 1))
                    .describedAs("above maximum bucket index at scale %s", testedScale)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("bucket range exceeds finite value range for scale %s", testedScale);
        }
    }

    @Test
    public void testDownscaleToAtMost()
    {
        ExponentialHistogram histogram = new ExponentialHistogram(1, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        histogram.record(1);
        histogram.record(4);

        histogram.downscaleToAtMost(0);
        histogram.downscaleToAtMost(1);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();
        assertThat(snapshot.scale()).isEqualTo(0);
        assertThat(snapshot.count()).isEqualTo(2);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {1, 0, 1}));
    }

    @Test
    public void testDownscaleToAtMostLowersEmptyHistogram()
    {
        ExponentialHistogram histogram = new ExponentialHistogram(5, ExponentialHistogram.DEFAULT_MAX_BUCKETS);

        histogram.downscaleToAtMost(3);

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();
        assertThat(snapshot.scale()).isEqualTo(3);
        assertThat(snapshot.count()).isEqualTo(0);
        assertThat(snapshot.positiveBuckets().isEmpty()).isTrue();
        assertThat(snapshot.negativeBuckets().isEmpty()).isTrue();
    }

    @Test
    public void testReset()
    {
        ExponentialHistogram histogram = new ExponentialHistogram(1, 3);
        histogram.record(1);
        histogram.record(4);
        histogram.record(0);
        assertThat(histogram.snapshot().scale()).isEqualTo(0);

        histogram.reset();

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();
        assertThat(snapshot.scale()).isEqualTo(1);
        assertThat(snapshot.count()).isEqualTo(0);
        assertThat(snapshot.sum()).isEqualTo(0);
        assertThat(snapshot.min()).isNaN();
        assertThat(snapshot.max()).isNaN();
        assertThat(snapshot.zeroCount()).isEqualTo(0);
        assertThat(snapshot.positiveBuckets().isEmpty()).isTrue();
        assertThat(snapshot.negativeBuckets().isEmpty()).isTrue();
    }

    @Test
    public void testIgnoresNonFiniteValues()
    {
        ExponentialHistogram histogram = new ExponentialHistogram();

        histogram.record(Double.NaN);
        histogram.record(Double.POSITIVE_INFINITY);
        histogram.record(Double.NEGATIVE_INFINITY);

        assertThat(histogram.snapshot().count()).isEqualTo(0);
    }

    @Test
    public void testRejectsInvalidConfiguration()
    {
        assertThatThrownBy(() -> new ExponentialHistogram(ExponentialHistogram.MAX_SCALE + 1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExponentialHistogram(0, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxBuckets must be at least 3");
    }

    @Test
    public void testBucketBounds()
    {
        assertThat(ExponentialHistogram.bucketLowerBound(0, 0)).isEqualTo(1);
        assertThat(ExponentialHistogram.bucketUpperBound(0, 0)).isEqualTo(2);
        assertThat(ExponentialHistogram.bucketUpperBound(0, -1)).isEqualTo(1);
        assertThat(ExponentialHistogram.bucketLowerBound(1, 2)).isEqualTo(2);
        assertThat(ExponentialHistogram.bucketUpperBound(1, 2)).isCloseTo(Math.sqrt(8), offset(1e-15));
    }

    @Test
    public void testPositiveScaleBucketIndexing()
    {
        for (int scale = 1; scale <= 10; scale++) {
            assertPositiveBucketIndex(scale, 1);
            assertPositiveBucketIndex(scale, 2);
            assertPositiveBucketIndex(scale, Math.nextUp(1));
            assertPositiveBucketIndex(scale, 1.1);
            assertPositiveBucketIndex(scale, 123.456);

            int bucketsPerPowerOfTwo = 1 << scale;
            for (int bucket = 0; bucket < bucketsPerPowerOfTwo; bucket += Math.max(1, bucketsPerPowerOfTwo / 8)) {
                double upperBound = ExponentialHistogram.bucketUpperBound(scale, bucket);
                assertPositiveBucketIndex(scale, upperBound);
                assertPositiveBucketIndex(scale, Math.nextUp(upperBound));
            }
        }
    }

    @Test
    public void testConcurrentRecording()
            throws Exception
    {
        ExponentialHistogram histogram = new ExponentialHistogram(0, 10);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<? extends Future<?>> futures = IntStream.range(0, 4)
                    .mapToObj(_ -> executor.submit(() -> {
                        for (int i = 0; i < 1_000; i++) {
                            histogram.record(1);
                        }
                    }))
                    .toList();

            for (Future<?> future : futures) {
                future.get();
            }
        }
        finally {
            executor.shutdownNow();
        }

        ExponentialHistogramSnapshot snapshot = histogram.snapshot();

        assertThat(snapshot.count()).isEqualTo(4_000);
        assertThat(snapshot.sum()).isEqualTo(4_000);
        assertThat(snapshot.positiveBuckets()).isEqualTo(new Buckets(-1, new long[] {4_000}));
    }

    private static void assertPositiveBucketIndex(int scale, double value)
    {
        ExponentialHistogram histogram = new ExponentialHistogram(scale, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        histogram.record(value);

        assertThat(histogram.snapshot().positiveBuckets().offset())
                .describedAs("bucket index for scale %s and value %s", scale, value)
                .isEqualTo(expectedBucketIndex(scale, value));
    }

    private static int bucketIndexForValue(int scale, double value)
    {
        ExponentialHistogram histogram = new ExponentialHistogram(scale, ExponentialHistogram.DEFAULT_MAX_BUCKETS);
        histogram.record(value);
        return histogram.snapshot().positiveBuckets().offset();
    }

    private static ExponentialHistogramSnapshot snapshotWithPositiveBucket(int scale, int bucketIndex)
    {
        return new ExponentialHistogramSnapshot(
                scale,
                1,
                1,
                1,
                1,
                0,
                new Buckets(bucketIndex, new long[] {1}),
                new Buckets(0, new long[0]));
    }

    private static int expectedBucketIndex(int scale, double value)
    {
        int index = (int) Math.ceil(Math.log(value) / Math.log(2) * Math.scalb(1.0, scale)) - 1;
        while (value <= ExponentialHistogram.bucketLowerBound(scale, index)) {
            index--;
        }
        while (value > ExponentialHistogram.bucketUpperBound(scale, index)) {
            index++;
        }
        return index;
    }
}
