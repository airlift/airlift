package io.airlift.stats;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;

import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Double.isFinite;
import static java.lang.Math.ceil;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.scalb;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

/**
 * Records values into base-2 exponential buckets.
 * <p>
 * Bucket boundaries are computed from a scale. At scale 0, buckets are powers of two:
 *
 * <pre>
 * ..., (0.25, 0.5], (0.5, 1], (1, 2], (2, 4], ...
 * </pre>
 *
 * Higher scales add more buckets between adjacent powers of two. In general, {@code base =
 * 2^(2^-scale)} and bucket {@code i} covers {@code (base^i, base^(i + 1)]}. The histogram starts at
 * the configured scale for high precision. When the observed bucket range no longer fits within the
 * configured maximum bucket count, the histogram lowers its scale and merges adjacent buckets. This
 * keeps the full observed range while reducing precision.
 * <p>
 * Positive and negative values are stored in separate bucket ranges. Negative values are bucketed by
 * their absolute magnitude, and zero values are counted separately.
 * <p>
 * This design follows the OpenTelemetry exponential histogram data model and is intentionally
 * aligned with the OpenTelemetry Java SDK's base-2 exponential histogram aggregation.
 */
@ThreadSafe
public final class ExponentialHistogram
{
    public static final int DEFAULT_SCALE = 20;
    public static final int DEFAULT_MAX_BUCKETS = 160;
    public static final int MIN_SCALE = -10;
    public static final int MAX_SCALE = 20;

    private static final long EXPONENT_BIT_MASK = 0x7FF0000000000000L;
    private static final long SIGNIFICAND_BIT_MASK = 0xFFFFFFFFFFFFFL;
    private static final int EXPONENT_BIAS = 1023;
    private static final int SIGNIFICAND_WIDTH = 52;
    private static final int EXPONENT_WIDTH = 11;
    private static final double LOG_2 = log(2);
    private static final int LOOKUP_MAX_SCALE = 10;
    private static final LookupBucketIndexer[] LOOKUP_BUCKET_INDEXERS = createLookupBucketIndexers();
    private static final BucketIndexRange[] FINITE_BUCKET_INDEX_RANGES = createFiniteBucketIndexRanges();
    private static final int MIN_BUCKETS_FOR_FULL_FINITE_RANGE = (int) FINITE_BUCKET_INDEX_RANGES[0].length();

    private final int initialScale;
    private final int maxBuckets;

    @GuardedBy("this")
    private int scale;
    @GuardedBy("this")
    private long count;
    @GuardedBy("this")
    private double sum;
    @GuardedBy("this")
    private double min = Double.NaN;
    @GuardedBy("this")
    private double max = Double.NaN;
    @GuardedBy("this")
    private long zeroCount;
    @GuardedBy("this")
    private BucketCounts positiveBuckets = new BucketCounts();
    @GuardedBy("this")
    private BucketCounts negativeBuckets = new BucketCounts();

    public ExponentialHistogram()
    {
        this(DEFAULT_SCALE, DEFAULT_MAX_BUCKETS);
    }

    public ExponentialHistogram(int scale, int maxBuckets)
    {
        checkScale(scale);
        checkMaxBuckets(maxBuckets);
        this.initialScale = scale;
        this.scale = scale;
        this.maxBuckets = maxBuckets;
    }

    ExponentialHistogram(ExponentialHistogramSnapshot snapshot, int maxBuckets)
    {
        requireNonNull(snapshot, "snapshot is null");
        checkMaxBuckets(maxBuckets);
        checkArgument(snapshot.positiveBuckets().counts().length <= maxBuckets, "positive bucket count exceeds maxBuckets");
        checkArgument(snapshot.negativeBuckets().counts().length <= maxBuckets, "negative bucket count exceeds maxBuckets");
        this.initialScale = snapshot.scale();
        this.maxBuckets = maxBuckets;
        this.scale = snapshot.scale();
        this.count = snapshot.count();
        this.sum = snapshot.sum();
        this.min = snapshot.min();
        this.max = snapshot.max();
        this.zeroCount = snapshot.zeroCount();
        this.positiveBuckets = new BucketCounts(snapshot.positiveBuckets());
        this.negativeBuckets = new BucketCounts(snapshot.negativeBuckets());
    }

    public synchronized void record(double value)
    {
        record(value, 1);
    }

    public synchronized void record(double value, long occurrences)
    {
        checkArgument(occurrences >= 0, "occurrences is negative");
        if (occurrences == 0) {
            return;
        }
        if (!isFinite(value)) {
            return;
        }

        count += occurrences;
        sum += value * occurrences;
        min = Double.isNaN(min) ? value : min(min, value);
        max = Double.isNaN(max) ? value : max(max, value);

        if (value == 0) {
            zeroCount += occurrences;
            return;
        }

        BucketCounts buckets = value > 0 ? positiveBuckets : negativeBuckets;
        double magnitude = Math.abs(value);
        int bucketIndex = bucketIndex(magnitude, scale);
        int scaleReduction = buckets.scaleReduction(bucketIndex, maxBuckets);
        if (scaleReduction > 0) {
            downscale(scaleReduction);
            bucketIndex = bucketIndex(magnitude, scale);
        }
        buckets.increment(bucketIndex, occurrences);
    }

    public synchronized void reset()
    {
        scale = initialScale;
        count = 0;
        sum = 0;
        min = Double.NaN;
        max = Double.NaN;
        zeroCount = 0;
        positiveBuckets = new BucketCounts();
        negativeBuckets = new BucketCounts();
    }

    public synchronized ExponentialHistogramSnapshot snapshot()
    {
        return new ExponentialHistogramSnapshot(
                scale,
                count,
                sum,
                min,
                max,
                zeroCount,
                positiveBuckets.snapshot(),
                negativeBuckets.snapshot());
    }

    public synchronized void downscaleToAtMost(int targetScale)
    {
        checkArgument(targetScale >= MIN_SCALE && targetScale <= MAX_SCALE, "targetScale must be between %s and %s", MIN_SCALE, MAX_SCALE);
        if (scale > targetScale) {
            downscale(scale - targetScale);
        }
    }

    public static double bucketLowerBound(int scale, int index)
    {
        checkScale(scale);
        return Math.pow(2, scalb(index, -scale));
    }

    public static double bucketUpperBound(int scale, int index)
    {
        checkScale(scale);
        return Math.pow(2, scalb(index + 1, -scale));
    }

    static double[] valuesAt(ExponentialHistogramSnapshot snapshot, double[] percentiles)
    {
        requireNonNull(snapshot, "snapshot is null");
        requireNonNull(percentiles, "percentiles is null");

        double[] values = new double[percentiles.length];
        if (snapshot.count() == 0) {
            Arrays.fill(values, Double.NaN);
            return values;
        }

        long[] ranks = new long[percentiles.length];
        for (int i = 0; i < percentiles.length; i++) {
            if (percentiles[i] <= 0) {
                ranks[i] = 1;
            }
            else if (percentiles[i] >= 1) {
                ranks[i] = snapshot.count();
            }
            else {
                ranks[i] = (long) ceil(percentiles[i] * snapshot.count());
            }
        }

        long seen = 0;
        int percentileIndex = 0;
        long[] negativeCounts = snapshot.negativeBuckets().counts();
        for (int i = negativeCounts.length - 1; i >= 0; i--) {
            seen += negativeCounts[i];
            double value = -bucketUpperBound(snapshot.scale(), snapshot.negativeBuckets().offset() + i);
            while (percentileIndex < ranks.length && seen >= ranks[percentileIndex]) {
                values[percentileIndex] = value;
                percentileIndex++;
            }
        }

        seen += snapshot.zeroCount();
        while (percentileIndex < ranks.length && seen >= ranks[percentileIndex]) {
            values[percentileIndex] = 0;
            percentileIndex++;
        }

        long[] positiveCounts = snapshot.positiveBuckets().counts();
        for (int i = 0; i < positiveCounts.length; i++) {
            seen += positiveCounts[i];
            double value = bucketUpperBound(snapshot.scale(), snapshot.positiveBuckets().offset() + i);
            while (percentileIndex < ranks.length && seen >= ranks[percentileIndex]) {
                values[percentileIndex] = value;
                percentileIndex++;
            }
        }

        while (percentileIndex < values.length) {
            values[percentileIndex] = snapshot.max();
            percentileIndex++;
        }
        return values;
    }

    @GuardedBy("this")
    private void downscale(int by)
    {
        if (by == 0) {
            return;
        }
        if (scale - by < MIN_SCALE) {
            throw new IllegalStateException("bucket range exceeds maxBuckets at minimum scale");
        }
        scale -= by;
        positiveBuckets.downscale(by);
        negativeBuckets.downscale(by);
    }

    private static int bucketIndex(double value, int scale)
    {
        if (scale == 0) {
            return mapToIndexScaleZero(value);
        }
        if (scale < 0) {
            return mapToIndexScaleZero(value) >> -scale;
        }
        if (scale <= LOOKUP_MAX_SCALE) {
            return LOOKUP_BUCKET_INDEXERS[scale].bucketIndex(value);
        }

        return logarithmicBucketIndex(value, scale);
    }

    private static int logarithmicBucketIndex(double value, int scale)
    {
        int index = (int) ceil(log(value) / LOG_2 * scalb(1.0, scale)) - 1;
        while (value <= bucketLowerBound(scale, index)) {
            index--;
        }
        while (value > bucketUpperBound(scale, index)) {
            index++;
        }
        return index;
    }

    private static int mapToIndexScaleZero(double value)
    {
        long rawBits = Double.doubleToLongBits(value);
        long rawExponent = (rawBits & EXPONENT_BIT_MASK) >> SIGNIFICAND_WIDTH;
        long rawSignificand = rawBits & SIGNIFICAND_BIT_MASK;
        if (rawExponent == 0) {
            rawExponent -= Long.numberOfLeadingZeros(rawSignificand - 1) - EXPONENT_WIDTH - 1;
        }
        int ieeeExponent = (int) (rawExponent - EXPONENT_BIAS);
        if (rawSignificand == 0) {
            return ieeeExponent - 1;
        }
        return ieeeExponent;
    }

    private static LookupBucketIndexer[] createLookupBucketIndexers()
    {
        LookupBucketIndexer[] indexers = new LookupBucketIndexer[LOOKUP_MAX_SCALE + 1];
        for (int scale = 1; scale <= LOOKUP_MAX_SCALE; scale++) {
            indexers[scale] = new LookupBucketIndexer(scale);
        }
        return indexers;
    }

    private static BucketIndexRange[] createFiniteBucketIndexRanges()
    {
        BucketIndexRange[] ranges = new BucketIndexRange[MAX_SCALE - MIN_SCALE + 1];
        for (int scale = MIN_SCALE; scale <= MAX_SCALE; scale++) {
            ranges[scale - MIN_SCALE] = new BucketIndexRange(
                    bucketIndex(Double.MIN_VALUE, scale),
                    bucketIndex(Double.MAX_VALUE, scale));
        }
        return ranges;
    }

    public record ExponentialHistogramSnapshot(
            int scale,
            long count,
            double sum,
            double min,
            double max,
            long zeroCount,
            Buckets positiveBuckets,
            Buckets negativeBuckets)
    {
        public ExponentialHistogramSnapshot
        {
            checkScale(scale);
            checkArgument(count >= 0, "count is negative");
            checkArgument(zeroCount >= 0, "zeroCount is negative");
            requireNonNull(positiveBuckets, "positiveBuckets is null");
            requireNonNull(negativeBuckets, "negativeBuckets is null");
            validateBucketRange(scale, positiveBuckets);
            validateBucketRange(scale, negativeBuckets);
        }

        public static ExponentialHistogramSnapshot merge(List<ExponentialHistogramSnapshot> snapshots)
        {
            return merge(snapshots, DEFAULT_MAX_BUCKETS);
        }

        public static ExponentialHistogramSnapshot merge(List<ExponentialHistogramSnapshot> snapshots, int maxBuckets)
        {
            requireNonNull(snapshots, "snapshots is null");
            checkMaxBuckets(maxBuckets);

            int targetScale = snapshots.stream()
                    .map(snapshot -> requireNonNull(snapshot, "snapshot is null").scale())
                    .min(Integer::compareTo)
                    .orElse(DEFAULT_SCALE);

            long count = 0;
            double sum = 0;
            double min = Double.NaN;
            double max = Double.NaN;
            long zeroCount = 0;
            for (ExponentialHistogramSnapshot snapshot : snapshots) {
                count += snapshot.count();
                sum += snapshot.sum();
                zeroCount += snapshot.zeroCount();
                if (snapshot.count() > 0) {
                    min = Double.isNaN(min) ? snapshot.min() : Math.min(min, snapshot.min());
                    max = Double.isNaN(max) ? snapshot.max() : Math.max(max, snapshot.max());
                }
            }

            targetScale = targetScaleFor(snapshots, targetScale, maxBuckets);
            return new ExponentialHistogramSnapshot(
                    targetScale,
                    count,
                    sum,
                    min,
                    max,
                    zeroCount,
                    mergeBuckets(snapshots, targetScale, maxBuckets, ExponentialHistogramSnapshot::positiveBuckets),
                    mergeBuckets(snapshots, targetScale, maxBuckets, ExponentialHistogramSnapshot::negativeBuckets));
        }
    }

    private static int targetScaleFor(List<ExponentialHistogramSnapshot> snapshots, int targetScale, int maxBuckets)
    {
        while (bucketRangeLength(snapshots, targetScale, ExponentialHistogramSnapshot::positiveBuckets) > maxBuckets ||
                bucketRangeLength(snapshots, targetScale, ExponentialHistogramSnapshot::negativeBuckets) > maxBuckets) {
            checkArgument(targetScale > MIN_SCALE, "bucket range exceeds maxBuckets at minimum scale");
            targetScale--;
        }
        return targetScale;
    }

    private static long bucketRangeLength(List<ExponentialHistogramSnapshot> snapshots, int targetScale, BucketSelector bucketSelector)
    {
        BucketIndexRange range = bucketIndexRange(snapshots, targetScale, bucketSelector);
        if (range.isEmpty()) {
            return 0;
        }
        return range.length();
    }

    private static Buckets mergeBuckets(List<ExponentialHistogramSnapshot> snapshots, int targetScale, int maxBuckets, BucketSelector bucketSelector)
    {
        BucketIndexRange range = bucketIndexRange(snapshots, targetScale, bucketSelector);
        if (range.isEmpty()) {
            return new Buckets(0, new long[0]);
        }

        long firstIndex = range.firstIndex();
        long length = range.length();
        checkArgument(length <= maxBuckets, "merged bucket range exceeds maxBuckets");
        long[] counts = new long[(int) length];
        for (ExponentialHistogramSnapshot snapshot : snapshots) {
            Buckets buckets = bucketSelector.buckets(snapshot);
            int scaleReduction = snapshot.scale() - targetScale;
            long[] bucketCounts = buckets.counts;
            for (int i = 0; i < bucketCounts.length; i++) {
                counts[(int) (downscaleBucketIndex((long) buckets.offset() + i, scaleReduction) - firstIndex)] += bucketCounts[i];
            }
        }
        return new Buckets((int) firstIndex, counts);
    }

    private static BucketIndexRange bucketIndexRange(List<ExponentialHistogramSnapshot> snapshots, int targetScale, BucketSelector bucketSelector)
    {
        long firstIndex = Long.MAX_VALUE;
        long lastIndex = Long.MIN_VALUE;
        for (ExponentialHistogramSnapshot snapshot : snapshots) {
            Buckets buckets = bucketSelector.buckets(snapshot);
            if (buckets.isEmpty()) {
                continue;
            }

            int scaleReduction = snapshot.scale() - targetScale;
            firstIndex = min(firstIndex, downscaleBucketIndex(buckets.offset(), scaleReduction));
            lastIndex = max(lastIndex, downscaleBucketIndex(lastIndex(buckets), scaleReduction));
        }
        return new BucketIndexRange(firstIndex, lastIndex);
    }

    private record BucketIndexRange(long firstIndex, long lastIndex)
    {
        boolean isEmpty()
        {
            return firstIndex == Long.MAX_VALUE;
        }

        long length()
        {
            return lastIndex - firstIndex + 1;
        }
    }

    private static long downscaleBucketIndex(long index, int scaleReduction)
    {
        return index >> scaleReduction;
    }

    private static void checkScale(int scale)
    {
        checkArgument(scale >= MIN_SCALE && scale <= MAX_SCALE, "scale must be between %s and %s", MIN_SCALE, MAX_SCALE);
    }

    private static void checkMaxBuckets(int maxBuckets)
    {
        checkArgument(maxBuckets >= MIN_BUCKETS_FOR_FULL_FINITE_RANGE, "maxBuckets must be at least %s", MIN_BUCKETS_FOR_FULL_FINITE_RANGE);
    }

    private static void validateBucketRange(int scale, Buckets buckets)
    {
        if (buckets.isEmpty()) {
            return;
        }
        BucketIndexRange finiteRange = FINITE_BUCKET_INDEX_RANGES[scale - MIN_SCALE];
        checkArgument(
                buckets.offset() >= finiteRange.firstIndex() &&
                        lastIndex(buckets) <= finiteRange.lastIndex(),
                "bucket range exceeds finite value range for scale %s",
                scale);
    }

    private static long lastIndex(Buckets buckets)
    {
        return (long) buckets.offset() + buckets.counts.length - 1;
    }

    private interface BucketSelector
    {
        Buckets buckets(ExponentialHistogramSnapshot snapshot);
    }

    /**
     * @param offset bucket index for {@code counts[0]}, or 0 when {@code counts} is empty
     * @param counts bucket counts starting at {@code offset}
     */
    public record Buckets(int offset, long[] counts)
    {
        public Buckets
        {
            counts = requireNonNull(counts, "counts is null").clone();
            stream(counts).forEach(count -> checkArgument(count >= 0, "bucket count is negative"));
        }

        public boolean isEmpty()
        {
            return counts.length == 0;
        }

        @Override
        public long[] counts()
        {
            return counts.clone();
        }

        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof Buckets other &&
                    offset == other.offset &&
                    Arrays.equals(counts, other.counts);
        }

        @Override
        public int hashCode()
        {
            return 31 * Integer.hashCode(offset) + Arrays.hashCode(counts);
        }

        @Override
        public String toString()
        {
            return "Buckets[offset=" + offset + ", counts=" + Arrays.toString(counts) + "]";
        }
    }

    private static final class BucketCounts
    {
        private int offset;
        private long[] counts = new long[0];

        private BucketCounts() {}

        private BucketCounts(Buckets buckets)
        {
            requireNonNull(buckets, "buckets is null");
            offset = buckets.offset();
            counts = buckets.counts();
        }

        public int scaleReduction(int index, int maxBuckets)
        {
            if (counts.length == 0) {
                return 0;
            }

            long newOffset = min(index, offset);
            long newLastIndex = max(index, lastIndex());
            int scaleReduction = 0;
            // Match the OpenTelemetry reduction rule by shifting the proposed inclusive bucket
            // range until it fits. This runs only when a new value expands the current bucket
            // window, and is bounded by MAX_SCALE - MIN_SCALE.
            while (newLastIndex - newOffset + 1 > maxBuckets) {
                newOffset >>= 1;
                newLastIndex >>= 1;
                scaleReduction++;
            }
            return scaleReduction;
        }

        public void increment(int index, long count)
        {
            if (counts.length == 0) {
                offset = index;
                counts = new long[] {count};
                return;
            }

            int newOffset = min(offset, index);
            int newLastIndex = max(lastIndex(), index);
            if (newOffset != offset || newLastIndex != lastIndex()) {
                long[] newCounts = new long[newLastIndex - newOffset + 1];
                System.arraycopy(counts, 0, newCounts, offset - newOffset, counts.length);
                counts = newCounts;
                offset = newOffset;
            }
            counts[index - offset] += count;
        }

        public void downscale(int by)
        {
            if (by == 0 || counts.length == 0) {
                return;
            }

            int newOffset = offset >> by;
            long[] newCounts = new long[(lastIndex() >> by) - newOffset + 1];
            for (int i = 0; i < counts.length; i++) {
                newCounts[((offset + i) >> by) - newOffset] += counts[i];
            }
            offset = newOffset;
            counts = newCounts;
        }

        public Buckets snapshot()
        {
            if (counts.length == 0) {
                return new Buckets(0, new long[0]);
            }
            return new Buckets(offset, counts);
        }

        private int lastIndex()
        {
            return offset + counts.length - 1;
        }
    }

    private static final class LookupBucketIndexer
    {
        private final int scale;
        private final long[] significandUpperBounds;

        private LookupBucketIndexer(int scale)
        {
            this.scale = scale;

            int bucketsPerPowerOfTwo = 1 << scale;
            significandUpperBounds = new long[bucketsPerPowerOfTwo - 1];
            for (int i = 0; i < significandUpperBounds.length; i++) {
                double upperBound = Math.pow(2, scalb(i + 1, -scale));
                significandUpperBounds[i] = Double.doubleToRawLongBits(upperBound) & SIGNIFICAND_BIT_MASK;
            }
        }

        private int bucketIndex(double value)
        {
            long rawBits = Double.doubleToRawLongBits(value);
            long rawExponent = (rawBits & EXPONENT_BIT_MASK) >> SIGNIFICAND_WIDTH;
            if (rawExponent == 0) {
                return logarithmicBucketIndex(value, scale);
            }

            int ieeeExponent = (int) (rawExponent - EXPONENT_BIAS);
            long rawSignificand = rawBits & SIGNIFICAND_BIT_MASK;
            if (rawSignificand == 0) {
                return (ieeeExponent << scale) - 1;
            }

            int position = Arrays.binarySearch(significandUpperBounds, rawSignificand);
            if (position < 0) {
                position = -position - 1;
            }
            return (ieeeExponent << scale) + position;
        }
    }
}
