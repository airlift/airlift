package io.airlift.stats;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.airlift.slice.Slice;
import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class TestQuantileDigest
{
    @Test
    public void testSingleAdd()
    {
        QuantileDigest digest = new QuantileDigest(1);
        digest.add(0);

        digest.validate();

        // should have no compressions with so few values and the allowed error
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);

        assertThat(digest.getCount()).isEqualTo(1);
        assertThat(digest.getNodeCount()).isEqualTo(1);
    }

    @Test
    public void testNegativeValues()
    {
        QuantileDigest digest = new QuantileDigest(1);
        addAll(digest, asList(-1, -2, -3, -4, -5, 0, 1, 2, 3, 4, 5));

        assertThat(digest.getCount()).isEqualTo(11);
    }

    @Test
    public void testRepeatedValue()
    {
        QuantileDigest digest = new QuantileDigest(1);
        digest.add(0);
        digest.add(0);

        digest.validate();

        // should have no compressions with so few values and the allowed error
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);

        assertThat(digest.getCount()).isEqualTo(2);
        assertThat(digest.getNodeCount()).isEqualTo(1);
    }

    @Test
    public void testTwoDistinctValues()
    {
        QuantileDigest digest = new QuantileDigest(1);
        digest.add(0);
        digest.add(Long.MAX_VALUE);

        digest.validate();

        // should have no compressions with so few values and the allowed error
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);
        assertThat(digest.getCount()).isEqualTo(2);
        assertThat(digest.getNodeCount()).isEqualTo(3);
    }

    @Test
    public void testTreeBuilding()
    {
        QuantileDigest digest = new QuantileDigest(1);

        List<Integer> values = asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7);
        addAll(digest, values);

        assertThat(digest.getCount()).isEqualTo(values.size());
    }

    @Test
    public void testTreeBuildingReverse()
    {
        QuantileDigest digest = new QuantileDigest(1);

        List<Integer> values = asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7);
        addAll(digest, Lists.reverse(values));

        assertThat(digest.getCount()).isEqualTo(values.size());
    }

    @Test
    public void testBasicCompression()
    {
        // maxError = 0.8 so that we get compression factor = 5 with the data below
        QuantileDigest digest = new QuantileDigest(0.8, 0, new TestingTicker());

        List<Integer> values = asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7);
        addAll(digest, values);

        digest.compress();
        digest.validate();

        assertThat(digest.getCount()).isEqualTo(values.size());
        assertThat(digest.getNodeCount()).isEqualTo(7);
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.2);
    }

    @Test
    public void testCompression()
    {
        QuantileDigest digest = new QuantileDigest(1, 0, new TestingTicker());

        for (int loop = 0; loop < 2; ++loop) {
            addRange(digest, 0, 15);

            digest.compress();
            digest.validate();
        }
    }

    @Test
    public void testQuantile()
    {
        QuantileDigest digest = new QuantileDigest(1);

        addAll(digest, asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

        // should have no compressions with so few values and the allowed error
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);

        assertThat(digest.getQuantile(0.0)).isEqualTo(0);
        assertThat(digest.getQuantile(0.1)).isEqualTo(1);
        assertThat(digest.getQuantile(0.2)).isEqualTo(2);
        assertThat(digest.getQuantile(0.3)).isEqualTo(3);
        assertThat(digest.getQuantile(0.4)).isEqualTo(4);
        assertThat(digest.getQuantile(0.5)).isEqualTo(5);
        assertThat(digest.getQuantile(0.6)).isEqualTo(6);
        assertThat(digest.getQuantile(0.7)).isEqualTo(7);
        assertThat(digest.getQuantile(0.8)).isEqualTo(8);
        assertThat(digest.getQuantile(0.9)).isEqualTo(9);
        assertThat(digest.getQuantile(1)).isEqualTo(9);
    }

    @Test
    public void testQuantileLowerBound()
    {
        QuantileDigest digest = new QuantileDigest(0.5);

        addRange(digest, 1, 100);

        assertThat(digest.getQuantileLowerBound(0.0)).isEqualTo(1);
        for (int i = 1; i <= 10; i++) {
            assertThat(digest.getQuantileLowerBound(i / 10.0) <= i * 10).isTrue();
            if (i > 5) {
                assertThat(digest.getQuantileLowerBound(i / 10.0) >= (i - 5) * 10).isTrue();
            }
        }

        assertThat(digest.getQuantilesLowerBound(ImmutableList.of(0.0, 0.1, 0.2))).isEqualTo(ImmutableList.of(digest.getQuantileLowerBound(0.0), digest.getQuantileLowerBound(0.1), digest.getQuantileLowerBound(0.2)));
    }

    @Test
    public void testQuantileUpperBound()
    {
        QuantileDigest digest = new QuantileDigest(0.5);

        addRange(digest, 1, 100);

        assertThat(digest.getQuantileUpperBound(1.0)).isEqualTo(99);
        for (int i = 0; i < 10; i++) {
            assertThat(digest.getQuantileUpperBound(i / 10.0) >= i * 10).isTrue();
            if (i < 5) {
                assertThat(digest.getQuantileUpperBound(i / 10.0) <= (i + 5) * 10).isTrue();
            }
        }

        assertThat(digest.getQuantilesUpperBound(ImmutableList.of(0.8, 0.9, 1.0))).isEqualTo(ImmutableList.of(digest.getQuantileUpperBound(0.8), digest.getQuantileUpperBound(0.9), digest.getQuantileUpperBound(1.0)));
    }

    @Test
    public void testWeightedValues()
    {
        QuantileDigest digest = new QuantileDigest(1);

        digest.add(0, 3);
        digest.add(2, 1);
        digest.add(4, 5);
        digest.add(5, 1);
        digest.validate();

        // should have no compressions with so few values and the allowed error
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);

        assertThat(digest.getQuantile(0.0)).isEqualTo(0);
        assertThat(digest.getQuantile(0.1)).isEqualTo(0);
        assertThat(digest.getQuantile(0.2)).isEqualTo(0);
        assertThat(digest.getQuantile(0.3)).isEqualTo(2);
        assertThat(digest.getQuantile(0.4)).isEqualTo(4);
        assertThat(digest.getQuantile(0.5)).isEqualTo(4);
        assertThat(digest.getQuantile(0.6)).isEqualTo(4);
        assertThat(digest.getQuantile(0.7)).isEqualTo(4);
        assertThat(digest.getQuantile(0.8)).isEqualTo(4);
        assertThat(digest.getQuantile(0.9)).isEqualTo(5);
        assertThat(digest.getQuantile(1)).isEqualTo(5);
    }

    @Test
    public void testBatchQuantileQuery()
    {
        QuantileDigest digest = new QuantileDigest(1);

        addAll(digest, asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

        // should have no compressions with so few values and the allowed error
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);

        assertThat(digest.getQuantiles(asList(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0))).isEqualTo(asList(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 9L));
    }

    @Test
    public void testHistogramQuery()
    {
        QuantileDigest digest = new QuantileDigest(1);

        addAll(digest, asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

        // should have no compressions with so few values and the allowed error
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);

        assertThat(digest.getHistogram(asList(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))).isEqualTo(asList(new QuantileDigest.Bucket(0, Double.NaN),
                new QuantileDigest.Bucket(1, 0),
                new QuantileDigest.Bucket(1, 1),
                new QuantileDigest.Bucket(1, 2),
                new QuantileDigest.Bucket(1, 3),
                new QuantileDigest.Bucket(1, 4),
                new QuantileDigest.Bucket(1, 5),
                new QuantileDigest.Bucket(1, 6),
                new QuantileDigest.Bucket(1, 7),
                new QuantileDigest.Bucket(1, 8),
                new QuantileDigest.Bucket(1, 9)));

        assertThat(digest.getHistogram(asList(7L, 10L))).isEqualTo(asList(new QuantileDigest.Bucket(7, 3),
                new QuantileDigest.Bucket(3, 8)));

        // test some edge conditions
        assertThat(digest.getHistogram(asList(0L))).isEqualTo(asList(new QuantileDigest.Bucket(0, Double.NaN)));
        assertThat(digest.getHistogram(asList(9L))).isEqualTo(asList(new QuantileDigest.Bucket(9, 4)));
        assertThat(digest.getHistogram(asList(10L))).isEqualTo(asList(new QuantileDigest.Bucket(10, 4.5)));
        assertThat(digest.getHistogram(asList(Long.MAX_VALUE))).isEqualTo(asList(new QuantileDigest.Bucket(10, 4.5)));
    }

    @Test
    public void testHistogramOfDoublesQuery()
    {
        QuantileDigest digest = new QuantileDigest(1);

        LongStream.range(-10, 10)
                .map(TestQuantileDigest::doubleToSortableLong)
                .boxed()
                .forEach(digest::add);

        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);

        List<Long> bucketUpperBounds = LongStream.range(-10, 10)
                .map(TestQuantileDigest::doubleToSortableLong)
                .boxed()
                .collect(toImmutableList());

        QuantileDigest.MiddleFunction middleFunction = (lowerBound, upperBound) -> {
            // qdigest will put the range at the top of the tree as the entire set of long values.  Sortable long values
            // which equal Long.MIN_VALUE or Long.MAX_VALUE are NaN values in IEEE 754 standard, therefore they can't
            // be accurately represented as floating point numbers.  Because NaN values cannot be used in the middle
            // calculation, treat them as Double.MIN_VALUE when the min is encountered, and Double.MAX_VALUE when the max
            // is encountered.
            double left = lowerBound > Long.MIN_VALUE ? sortableLongToDouble(lowerBound) : -1 * Double.MAX_VALUE;
            double right = upperBound < Long.MAX_VALUE ? sortableLongToDouble(upperBound) : Double.MAX_VALUE;
            return left + (right - left) / 2;
        };

        List<QuantileDigest.Bucket> expected = LongStream.range(-9, 10)
                .boxed()
                .map(i -> new QuantileDigest.Bucket(1, i - 1))
                .collect(Collectors.toList());
        expected.addFirst(new QuantileDigest.Bucket(0, Double.NaN));
        assertThat(digest.getHistogram(bucketUpperBounds, middleFunction)).isEqualTo(expected);

        assertThat(digest.getHistogram(asList(doubleToSortableLong(7), doubleToSortableLong(10)), middleFunction)).isEqualTo(asList(new QuantileDigest.Bucket(17, -2.0),
                new QuantileDigest.Bucket(3, 8)));

        // edge cases
        assertThat(digest.getHistogram(asList(doubleToSortableLong(-1 * Double.MAX_VALUE)), middleFunction)).isEqualTo(asList(new QuantileDigest.Bucket(0, Double.NaN)));
        assertThat(digest.getHistogram(asList(doubleToSortableLong(-1 * Double.MAX_VALUE), doubleToSortableLong(-1 * Double.MAX_VALUE + 1)), middleFunction)).isEqualTo(asList(new QuantileDigest.Bucket(0, Double.NaN), new QuantileDigest.Bucket(0, Double.NaN)));
        assertThat(digest.getHistogram(asList(doubleToSortableLong(0)), middleFunction)).isEqualTo(asList(new QuantileDigest.Bucket(10.0, -5.5)));
        assertThat(digest.getHistogram(asList(doubleToSortableLong(9)), middleFunction)).isEqualTo(asList(new QuantileDigest.Bucket(19, -1.0)));
        assertThat(digest.getHistogram(asList(doubleToSortableLong(10)), middleFunction)).isEqualTo(asList(new QuantileDigest.Bucket(20, -0.5)));
        assertThat(digest.getHistogram(asList(doubleToSortableLong(Double.MAX_VALUE)), middleFunction)).isEqualTo(asList(new QuantileDigest.Bucket(20, -0.5)));
    }

    @Test
    public void testHistogramQueryAfterCompression()
    {
        QuantileDigest digest = new QuantileDigest(0.1);

        int total = 10000;
        addRange(digest, 0, total);

        // compression should've run at this error rate and count
        assertThat(digest.getConfidenceFactor() > 0.0).isTrue();

        double actualMaxError = digest.getConfidenceFactor();

        for (long value = 0; value < total; ++value) {
            QuantileDigest.Bucket bucket = digest.getHistogram(asList(value)).getFirst();

            // estimated count should have an absolute error smaller than 2 * maxError * N
            assertThat(Math.abs(bucket.getCount() - value)).isLessThan(2 * actualMaxError * total);
        }
    }

    @Test
    public void testQuantileQueryError()
    {
        double maxError = 0.1;

        QuantileDigest digest = new QuantileDigest(maxError);

        int count = 10000;
        addRange(digest, 0, count);

        // compression should've run at this error rate and count
        assertThat(digest.getConfidenceFactor()).isGreaterThan(0);
        assertThat(digest.getConfidenceFactor()).isLessThan(maxError);

        for (int value = 0; value < count; ++value) {
            double quantile = value * 1.0 / count;
            long estimatedValue = digest.getQuantile(quantile);

            // true rank of estimatedValue is == estimatedValue because
            // we've inserted a list of ordered numbers starting at 0
            double error = Math.abs(estimatedValue - quantile * count) / count;

            assertThat(error < maxError).isTrue();
        }
    }

    @Test
    public void testDecayedQuantiles()
    {
        TestingTicker ticker = new TestingTicker();
        QuantileDigest digest = new QuantileDigest(1, ExponentialDecay.computeAlpha(0.5, 60), ticker);

        addAll(digest, asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

        // should have no compressions with so few values and the allowed error
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);

        ticker.increment(60, TimeUnit.SECONDS);
        addAll(digest, asList(10, 11, 12, 13, 14, 15, 16, 17, 18, 19));

        // Considering that the first 10 values now have a weight of 0.5 per the alpha factor, they only contributed a count
        // of 5 to rank computations. Therefore, the 50th percentile is equivalent to a weighted rank of (5 + 10) / 2 = 7.5,
        // which corresponds to value 12
        assertThat(digest.getQuantile(0.5)).isEqualTo(12);
    }

    @Test
    public void testDecayedCounts()
    {
        TestingTicker ticker = new TestingTicker();
        QuantileDigest digest = new QuantileDigest(1, ExponentialDecay.computeAlpha(0.5, 60), ticker);

        addAll(digest, asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

        // should have no compressions with so few values and the allowed error
        assertThat(digest.getConfidenceFactor()).isEqualTo(0.0);

        ticker.increment(60, TimeUnit.SECONDS);
        addAll(digest, asList(10, 11, 12, 13, 14, 15, 16, 17, 18, 19));

        assertThat(digest.getCount()).isEqualTo(15.0);
    }

    @Test
    public void testDecayedCountsWithClockIncrementSmallerThanRescaleThreshold()
    {
        int targetAgeInSeconds = (int) (QuantileDigest.RESCALE_THRESHOLD_SECONDS - 1);

        TestingTicker ticker = new TestingTicker();
        QuantileDigest digest = new QuantileDigest(1,
                ExponentialDecay.computeAlpha(0.5, targetAgeInSeconds), ticker);

        addAll(digest, asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        ticker.increment(targetAgeInSeconds, TimeUnit.SECONDS);
        addAll(digest, asList(10, 11, 12, 13, 14, 15, 16, 17, 18, 19));

        assertThat(digest.getCount()).isEqualTo(15.0);
    }

    @Test
    public void testMinMax()
    {
        QuantileDigest digest = new QuantileDigest(0.01, 0, new TestingTicker());

        int from = 500;
        int to = 700;
        addRange(digest, from, to + 1);

        assertThat(digest.getMin()).isEqualTo(from);
        assertThat(digest.getMax()).isEqualTo(to);
    }

    @Test
    public void testMinMaxWithDecay()
    {
        TestingTicker ticker = new TestingTicker();

        QuantileDigest digest = new QuantileDigest(0.01,
                ExponentialDecay.computeAlpha(QuantileDigest.ZERO_WEIGHT_THRESHOLD, 60), ticker);

        addRange(digest, 1, 10);

        ticker.increment(1000, TimeUnit.SECONDS); // TODO: tighter bounds?

        int from = 4;
        int to = 7;
        addRange(digest, from, to + 1);

        digest.validate();

        assertThat(digest.getMin()).isEqualTo(from);
        assertThat(digest.getMax()).isEqualTo(to);
    }

    @Test
    public void testRescaleWithDecayKeepsCompactTree()
    {
        TestingTicker ticker = new TestingTicker();
        int targetAgeInSeconds = (int) (QuantileDigest.RESCALE_THRESHOLD_SECONDS);

        QuantileDigest digest = new QuantileDigest(0.01,
                ExponentialDecay.computeAlpha(QuantileDigest.ZERO_WEIGHT_THRESHOLD / 2, targetAgeInSeconds),
                ticker);

        for (int i = 0; i < 10; ++i) {
            digest.add(i);
            digest.validate();

            // bump the clock to make all previous values decay to ~0
            ticker.increment(targetAgeInSeconds, TimeUnit.SECONDS);
        }

        assertThat(digest.getNodeCount()).isEqualTo(1);
    }

    @Test
    public void testEquivalenceEmpty()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);

        assertThat(a.equivalent(b)).isTrue();
    }

    @Test
    public void testEquivalenceSingle()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);

        a.add(1);
        b.add(1);

        assertThat(a.equivalent(b)).isTrue();
    }

    @Test
    public void testEquivalenceSingleDifferent()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);

        a.add(1);
        b.add(2);

        assertThat(a.equivalent(b)).isFalse();
    }

    @Test
    public void testEquivalenceComplex()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);

        addAll(a, asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7));
        addAll(b, asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7));

        assertThat(a.equivalent(b)).isTrue();
    }

    @Test
    public void testEquivalenceComplexDifferent()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);

        addAll(a, asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7));
        addAll(b, asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7, 8));

        assertThat(a.equivalent(b)).isFalse();
    }

    @Test
    public void testMergeEmpty()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);
        QuantileDigest pristineB = new QuantileDigest(0.01);

        a.merge(b);

        a.validate();
        b.validate();

        assertThat(b.equivalent(pristineB)).isTrue();

        assertThat(a.getCount()).isEqualTo(0.0);
        assertThat(a.getNodeCount()).isEqualTo(0);

        assertThat(b.getCount()).isEqualTo(0.0);
        assertThat(b.getNodeCount()).isEqualTo(0);
    }

    @Test
    public void testMergeIntoEmpty()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);
        QuantileDigest pristineB = new QuantileDigest(0.01);

        b.add(1);
        pristineB.add(1);

        a.merge(b);

        a.validate();
        b.validate();

        assertThat(b.equivalent(pristineB)).isTrue();

        assertThat(a.getCount()).isEqualTo(1.0);
        assertThat(a.getNodeCount()).isEqualTo(1);

        assertThat(b.getCount()).isEqualTo(1.0);
        assertThat(b.getNodeCount()).isEqualTo(1);
    }

    @Test
    public void testMergeWithEmpty()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);
        QuantileDigest pristineB = new QuantileDigest(0.01);

        a.add(1);
        a.merge(b);

        a.validate();
        b.validate();

        assertThat(b.equivalent(pristineB)).isTrue();

        assertThat(a.getCount()).isEqualTo(1.0);
        assertThat(a.getNodeCount()).isEqualTo(1);

        assertThat(b.getCount()).isEqualTo(0.0);
        assertThat(b.getNodeCount()).isEqualTo(0);
    }

    @Test
    public void testMergeSample()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);

        a.add(1);
        addAll(b, asList(2, 3));

        a.merge(b);

        a.validate();

        assertThat(a.getCount()).isEqualTo(3.0);
        assertThat(a.getNodeCount()).isEqualTo(5);
    }

    @Test
    public void testMergeSeparateBranches()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);
        QuantileDigest pristineB = new QuantileDigest(0.01);

        a.add(1);

        b.add(2);
        pristineB.add(2);

        a.merge(b);

        assertThat(b.equivalent(pristineB)).isTrue();

        assertThat(a.getCount()).isEqualTo(2.0);
        assertThat(a.getNodeCount()).isEqualTo(3);

        assertThat(b.getCount()).isEqualTo(1.0);
        assertThat(b.getNodeCount()).isEqualTo(1);
    }

    @Test
    public void testMergeWithLowerLevel()
    {
        QuantileDigest a = new QuantileDigest(1, 0, Ticker.systemTicker());
        QuantileDigest b = new QuantileDigest(1, 0, Ticker.systemTicker());
        QuantileDigest pristineB = new QuantileDigest(1, 0, Ticker.systemTicker());

        a.add(6);
        a.compress();

        List<Integer> values = asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5);

        addAll(b, values);
        b.compress();

        addAll(pristineB, values);
        pristineB.compress();

        a.merge(b);

        assertThat(b.equivalent(pristineB)).isTrue();

        assertThat(a.getCount()).isEqualTo(14.0);
        assertThat(b.getCount()).isEqualTo(13.0);
    }

    @Test
    public void testMergeWithHigherLevel()
    {
        QuantileDigest a = new QuantileDigest(1, 0, Ticker.systemTicker());
        QuantileDigest b = new QuantileDigest(1, 0, Ticker.systemTicker());
        QuantileDigest pristineB = new QuantileDigest(1, 0, Ticker.systemTicker());

        addAll(a, asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5));

        a.compress();

        addAll(b, asList(6, 7));
        addAll(pristineB, asList(6, 7));

        a.merge(b);

        assertThat(b.equivalent(pristineB)).isTrue();

        assertThat(a.getCount()).isEqualTo(15.0);
        assertThat(a.getNodeCount()).isEqualTo(7);

        assertThat(b.getCount()).isEqualTo(2.0);
        assertThat(b.getNodeCount()).isEqualTo(3);
    }

    // test merging two digests that have a node at the highest level to make sure
    // we handle boundary conditions properly
    @Test
    public void testMergeMaxLevel()
    {
        QuantileDigest a = new QuantileDigest(0.01);
        QuantileDigest b = new QuantileDigest(0.01);
        QuantileDigest pristineB = new QuantileDigest(0.01);

        addAll(a, asList(-1, 1));
        addAll(b, asList(-2, 2));
        addAll(pristineB, asList(-2, 2));
        a.merge(b);

        a.validate();
        b.validate();

        assertThat(b.equivalent(pristineB)).isTrue();

        assertThat(a.getCount()).isEqualTo(4.0);
        assertThat(a.getNodeCount()).isEqualTo(7);
    }

    @Test
    public void testMergeSameLevel()
    {
        QuantileDigest a = new QuantileDigest(1, 0, Ticker.systemTicker());
        QuantileDigest b = new QuantileDigest(1, 0, Ticker.systemTicker());
        QuantileDigest pristineB = new QuantileDigest(1, 0, Ticker.systemTicker());

        a.add(0);
        b.add(0);
        pristineB.add(0);

        a.merge(b);

        assertThat(b.equivalent(pristineB)).isTrue();

        assertThat(a.getCount()).isEqualTo(2.0);
        assertThat(a.getNodeCount()).isEqualTo(1);

        assertThat(b.getCount()).isEqualTo(1.0);
        assertThat(b.getNodeCount()).isEqualTo(1);
    }

    @Test
    public void testSerializationEmpty()
            throws Exception
    {
        QuantileDigest digest = new QuantileDigest(0.01);
        QuantileDigest deserialized = deserialize(digest.serialize());

        assertThat(digest.equivalent(deserialized)).isTrue();
    }

    @Test
    public void testSerializationSingle()
            throws Exception
    {
        QuantileDigest digest = new QuantileDigest(0.01);
        digest.add(1);

        assertThat(digest.equivalent(deserialize(digest.serialize()))).isTrue();
    }

    @Test
    public void testSerializationComplex()
            throws Exception
    {
        QuantileDigest digest = new QuantileDigest(1);
        addAll(digest, asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7));

        assertThat(digest.equivalent(deserialize(digest.serialize()))).isTrue();

        digest.compress();

        assertThat(digest.equivalent(deserialize(digest.serialize()))).isTrue();
    }

    @Test
    public void testSerializationWithExtremeEndsOfLong()
            throws Exception
    {
        QuantileDigest digest = new QuantileDigest(1);
        digest.add(Long.MIN_VALUE);
        digest.add(Long.MAX_VALUE);

        assertThat(digest.equivalent(deserialize(digest.serialize()))).isTrue();

        digest.compress();

        assertThat(digest.equivalent(deserialize(digest.serialize()))).isTrue();
    }

    @RepeatedTest(1000)
    public void testSerializationRandom()
            throws Exception
    {
        QuantileDigest digest = new QuantileDigest(1);

        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            values.add(ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE));
        }

        addAll(digest, values);

        assertThat(digest.equivalent(deserialize(digest.serialize()))).as(format("Serialization roundtrip failed for input: %s", values)).isTrue();
    }

    private QuantileDigest deserialize(Slice serialized)
    {
        QuantileDigest result = new QuantileDigest(serialized);
        result.validate();
        return result;
    }

    private void addAll(QuantileDigest digest, List<Integer> values)
    {
        for (int value : values) {
            digest.add(value);
        }
        digest.validate();
    }

    private void addRange(QuantileDigest digest, int from, int to)
    {
        for (int i = from; i < to; ++i) {
            digest.add(i);
        }
        digest.validate();
    }

    private static long doubleToSortableLong(double value)
    {
        long bits = Double.doubleToLongBits(value);
        return bits ^ ((bits >> 63) & Long.MAX_VALUE);
    }

    private static double sortableLongToDouble(long value)
    {
        value = value ^ ((value >> 63) & Long.MAX_VALUE);
        return Double.longBitsToDouble(value);
    }
}
