/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Doubles;
import io.airlift.slice.SizeOf;
import io.airlift.slice.Slice;
import io.airlift.slice.SliceInput;
import io.airlift.slice.SliceOutput;
import io.airlift.slice.Slices;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.slice.SizeOf.instanceSize;
import static java.lang.Double.isFinite;
import static java.lang.Double.isInfinite;
import static java.lang.Double.isNaN;
import static java.util.Objects.requireNonNull;

/**
 * This class is NOT thread safe.
 */
public class TDigest
{
    public static final double DEFAULT_COMPRESSION = 100;

    private static final int FORMAT_TAG = 0;
    private static final int T_DIGEST_SIZE = instanceSize(TDigest.class);
    private static final int INITIAL_CAPACITY = 1;
    private static final int FUDGE_FACTOR = 10;

    private final int maxSize;
    private final double compression;

    double[] means;
    double[] weights;
    int centroidCount;
    double totalWeight;

    double min;
    double max;

    private boolean backwards;
    private boolean needsMerge;

    private double[] tempMeans;
    private double[] tempWeights;

    // Length of the ascending-sorted prefix of means[]. Every merge() leaves the whole array sorted,
    // and add()/mergeWith() only append beyond it, so the next merge can sort just the unsorted tail
    // and merge the two runs instead of re-sorting the entire buffer.
    private int sortedPrefixLength;

    public TDigest()
    {
        this(DEFAULT_COMPRESSION);
    }

    public TDigest(double compression)
    {
        this(compression,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                0,
                0,
                new double[INITIAL_CAPACITY],
                new double[INITIAL_CAPACITY],
                false,
                false,
                0);
    }

    private TDigest(
            double compression,
            double min,
            double max,
            double totalWeight,
            int centroidCount,
            double[] means,
            double[] weights,
            boolean needsMerge,
            boolean backwards,
            int sortedPrefixLength)
    {
        checkArgument(compression >= 10, "compression factor too small (< 10)");

        this.compression = compression;
        this.maxSize = (int) (6 * (internalCompressionFactor(compression) + FUDGE_FACTOR)); // 5 * size + size (for centroids + new values)
        this.totalWeight = totalWeight;
        this.min = min;
        this.max = max;
        this.centroidCount = centroidCount;
        this.means = requireNonNull(means, "means is null");
        this.weights = requireNonNull(weights, "weights is null");
        this.needsMerge = needsMerge;
        this.backwards = backwards;
        this.sortedPrefixLength = sortedPrefixLength;
    }

    public static TDigest copyOf(TDigest other)
    {
        return new TDigest(
                other.compression,
                other.min,
                other.max,
                other.totalWeight,
                other.centroidCount,
                Arrays.copyOf(other.means, other.centroidCount),
                Arrays.copyOf(other.weights, other.centroidCount),
                other.needsMerge,
                other.backwards,
                // means[0, sortedPrefixLength) is preserved by the copy, so the next merge can
                // re-sort only the tail rather than the whole array
                other.sortedPrefixLength);
    }

    public static TDigest deserialize(Slice serialized)
    {
        SliceInput input = serialized.getInput();

        byte format = input.readByte();
        checkArgument(format == FORMAT_TAG, "Invalid format");

        double min = input.readDouble();
        double max = input.readDouble();
        double compression = input.readDouble();
        double totalWeight = input.readDouble();
        int centroidCount = input.readInt();

        double[] means = new double[centroidCount];
        for (int i = 0; i < centroidCount; i++) {
            means[i] = input.readDouble();
        }

        double[] weights = new double[centroidCount];
        for (int i = 0; i < centroidCount; i++) {
            weights[i] = input.readDouble();
        }

        return new TDigest(
                compression,
                min,
                max,
                totalWeight,
                centroidCount,
                means,
                weights,
                false,
                false,
                // serialize() merges before writing, so the deserialized centroids are fully sorted
                centroidCount);
    }

    public double getMin()
    {
        if (totalWeight == 0) {
            return Double.NaN;
        }
        return min;
    }

    public double getMax()
    {
        if (totalWeight == 0) {
            return Double.NaN;
        }
        return max;
    }

    public double getCount()
    {
        return totalWeight;
    }

    public void add(double value)
    {
        add(value, 1);
    }

    public void add(double value, double weight)
    {
        // Fast path: isFinite() rejects both NaN and ±Infinity in a single check, collapsing the
        // common case to one branch and deferring the per-argument checks (which produce specific
        // messages) to the rare invalid case.
        if (!isFinite(value) || !isFinite(weight)) {
            checkArgument(!isNaN(value), "value is NaN");
            checkArgument(!isNaN(weight), "weight is NaN");
            checkArgument(!isInfinite(value), "value must be finite");
            checkArgument(!isInfinite(weight), "weight must be finite");
        }

        if (centroidCount == means.length) {
            if (means.length < maxSize) {
                ensureCapacity(Math.min(Math.max(means.length * 2, INITIAL_CAPACITY), maxSize));
            }
            else {
                merge(internalCompressionFactor(compression));
                if (centroidCount >= means.length) {
                    throw new AssertionError("Invalid size estimation for T-Digest: " + Base64.getEncoder().encodeToString(serializeInternal().getBytes()));
                }
            }
        }

        means[centroidCount] = value;
        weights[centroidCount] = weight;
        centroidCount++;

        totalWeight += weight;
        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }

        needsMerge = true;
    }

    public void mergeWith(TDigest other)
    {
        if (centroidCount + other.centroidCount > means.length) {
            // first, try to compact the digests to make room
            merge(internalCompressionFactor(compression));
            other.merge(internalCompressionFactor(compression));

            // but if that's not sufficient to fit all clusters, grow the arrays
            ensureCapacity(centroidCount + other.centroidCount);
        }

        System.arraycopy(other.means, 0, means, centroidCount, other.centroidCount);
        System.arraycopy(other.weights, 0, weights, centroidCount, other.centroidCount);

        centroidCount += other.centroidCount;
        totalWeight += other.totalWeight;

        min = Math.min(min, other.min);
        max = Math.max(max, other.max);

        needsMerge = true;
    }

    /**
     * Releases unused capacity, shrinking this digest to its current set of centroids.
     *
     * <p>Pending additions are compressed, the centroid arrays are trimmed to the live centroid
     * count, and the transient merge buffers are released. This is intended for digests that are
     * done being fed (for example, cached or held in memory in large numbers): the bulk of the
     * insert headroom and all of the merge scratch are reclaimed. A subsequent {@link #add} or
     * {@link #mergeWith} simply re-grows the arrays on demand.
     */
    public void compact()
    {
        mergeIfNeeded(internalCompressionFactor(compression));

        // drop the insert headroom kept in means[]/weights[]
        if (means.length > centroidCount) {
            means = Arrays.copyOf(means, centroidCount);
            weights = Arrays.copyOf(weights, centroidCount);
        }

        // drop the transient merge scratch; ensureMergeBuffersCapacity() reallocates it if the digest is fed again
        tempMeans = null;
        tempWeights = null;
    }

    public double valueAt(double quantile)
    {
        return valuesAt(quantile)[0];
    }

    public List<Double> valuesAt(List<Double> quantiles)
    {
        return Doubles.asList(valuesAt(Doubles.toArray(quantiles)));
    }

    public double[] valuesAt(double... quantiles)
    {
        if (quantiles.length == 0) {
            return new double[0];
        }

        validateQuantilesArgument(quantiles);

        double[] result = new double[quantiles.length];

        if (centroidCount == 0) {
            Arrays.fill(result, Double.NaN);
            return result;
        }

        mergeIfNeeded(internalCompressionFactor(compression));

        if (centroidCount == 1) {
            Arrays.fill(result, means[0]);
            return result;
        }

        // offsets into the theoretical sequence of all values
        for (int i = 0; i < result.length; i++) {
            result[i] = quantiles[i] * totalWeight;
        }

        int index = 0;
        // lowest value
        while (index < result.length && result[index] < 1) {
            result[index] = min;
            index++;
        }
        // between bottom and first centroid
        while (index < result.length && result[index] < weights[0] / 2) {
            result[index] = (min + interpolate(result[index], 1, min, weights[0] / 2, means[0]));
            index++;
        }
        // between last centroid and top, but not the greatest value
        while (index < result.length && result[index] <= totalWeight - 1 && totalWeight - result[index] <= weights[centroidCount - 1] / 2 && weights[centroidCount - 1] / 2 > 1) {
            // we interpolate back from the end, so the value is negative
            result[index] = (max + interpolate(totalWeight - result[index], 1, max, weights[centroidCount - 1] / 2, means[centroidCount - 1]));
            index++;
        }
        // greatest value
        if (index < result.length && result[index] >= totalWeight - 1) {
            Arrays.fill(result, index, result.length, max);
            return result;
        }

        double weightSoFar = weights[0] / 2;
        int currentCentroid = 0;
        while (index < result.length) {
            double delta = (weights[currentCentroid] + weights[currentCentroid + 1]) / 2;
            while (currentCentroid < centroidCount - 1 && weightSoFar + delta <= result[index]) {
                weightSoFar += delta;
                currentCentroid++;
                if (currentCentroid < centroidCount - 1) {
                    delta = (weights[currentCentroid] + weights[currentCentroid + 1]) / 2;
                }
            }
            // past the last centroid
            if (currentCentroid == centroidCount - 1) {
                // between last centroid and top, but not the greatest value
                while (index < result.length && result[index] <= totalWeight - 1 && weights[centroidCount - 1] / 2 > 1) {
                    // we interpolate back from the end, so the value is negative
                    result[index] = (max + interpolate(totalWeight - result[index], 1, max, weights[centroidCount - 1] / 2, means[centroidCount - 1]));
                    index++;
                }
                // greatest value
                if (index < result.length) {
                    Arrays.fill(result, index, result.length, max);
                }
                return result;
            }
            else {
                // single-sample cluster on the left (current centroid) and the quantile falls within that cluster
                if (weights[currentCentroid] == 1 && result[index] - weightSoFar < weights[currentCentroid] / 2) {
                    result[index] = means[currentCentroid];
                }
                // single-sample cluster on the right (next centroid) and the quantile falls within that cluster
                else if (weights[currentCentroid + 1] == 1 && result[index] - weightSoFar >= weights[currentCentroid] / 2) {
                    result[index] = means[currentCentroid + 1];
                }
                // the quantile falls within a multi-sample cluster. If the other cluster is single-sample, we can exclude it from interpolation
                else {
                    double interpolationOffset = result[index] - weightSoFar;
                    double interpolationSectionLength = delta;
                    if (weights[currentCentroid] == 1) {
                        interpolationOffset -= weights[currentCentroid] / 2;
                        interpolationSectionLength = weights[currentCentroid + 1] / 2;
                    }
                    else if (weights[currentCentroid + 1] == 1) {
                        interpolationSectionLength = weights[currentCentroid] / 2;
                    }
                    result[index] = (means[currentCentroid] + interpolate(interpolationOffset, 0, means[currentCentroid], interpolationSectionLength, means[currentCentroid + 1]));
                }
                index++;
            }
        }

        return result;
    }

    private static void validateQuantilesArgument(double[] quantiles)
    {
        for (int i = 0; i < quantiles.length; i++) {
            double quantile = quantiles[i];
            if (i > 0 && quantile < quantiles[i - 1]) {
                throw new IllegalArgumentException("quantiles must be sorted in increasing order");
            }
            else if (quantile < 0 || quantile > 1) {
                throw new IllegalArgumentException("quantiles should be in [0, 1] range");
            }
        }
    }

    public Slice serialize()
    {
        merge(compression);
        return serializeInternal();
    }

    private Slice serializeInternal()
    {
        Slice result = Slices.allocate(serializedSizeInBytes());
        SliceOutput output = result.getOutput();

        output.writeByte(TDigest.FORMAT_TAG);
        output.writeDouble(min);
        output.writeDouble(max);
        output.writeDouble(compression);
        output.writeDouble(totalWeight);
        output.writeInt(centroidCount);
        for (int i = 0; i < centroidCount; i++) {
            output.writeDouble(means[i]);
        }
        for (int i = 0; i < centroidCount; i++) {
            output.writeDouble(weights[i]);
        }

        checkState(!output.isWritable(), "Expected serialized size doesn't match actual written size");

        return result;
    }

    public int serializedSizeInBytes()
    {
        return SizeOf.SIZE_OF_BYTE + // format
                SizeOf.SIZE_OF_DOUBLE + // min
                SizeOf.SIZE_OF_DOUBLE + // max
                SizeOf.SIZE_OF_DOUBLE + // compression
                SizeOf.SIZE_OF_DOUBLE + // totalWeight
                SizeOf.SIZE_OF_INT + // centroid count
                SizeOf.SIZE_OF_DOUBLE * centroidCount + // means
                SizeOf.SIZE_OF_DOUBLE * centroidCount; // weights
    }

    public int estimatedInMemorySizeInBytes()
    {
        return (int) (T_DIGEST_SIZE +
                SizeOf.sizeOf(means) +
                SizeOf.sizeOf(weights) +
                SizeOf.sizeOf(tempMeans) +
                SizeOf.sizeOf(tempWeights));
    }

    private void merge(double compression)
    {
        if (centroidCount == 0) {
            return;
        }

        ensureMergeBuffersCapacity();

        // leave means[0, centroidCount) (and weights, in lockstep) fully sorted ascending
        sortCentroids();

        // Process the sorted centroids in alternating directions on successive merges to avoid
        // systematic bias. Rather than physically reversing means[]/weights[] for a backward pass,
        // walk them from the high end: the compacted output still lands in the temp buffers in
        // processing order and is reversed once below to restore ascending order.
        int step = backwards ? -1 : 1;
        int start = backwards ? centroidCount - 1 : 0;
        int end = backwards ? -1 : centroidCount;

        double centroidMean = means[start];
        double centroidWeight = weights[start];

        int lastCentroid = 0;
        tempMeans[lastCentroid] = centroidMean;
        tempWeights[lastCentroid] = centroidWeight;

        double weightSoFar = 0;
        double normalizer = normalizer(compression, totalWeight);
        double currentQuantile = 0;
        double currentQuantileMaxClusterSize = maxRelativeClusterSize(currentQuantile, normalizer);

        for (int i = start + step; i != end; i += step) {
            double entryWeight = weights[i];
            double entryMean = means[i];

            double tentativeWeight = centroidWeight + entryWeight;
            double tentativeQuantile = Math.min((weightSoFar + tentativeWeight) / totalWeight, 1);

            double maxClusterWeight = totalWeight * Math.min(currentQuantileMaxClusterSize, maxRelativeClusterSize(tentativeQuantile, normalizer));
            if (tentativeWeight <= maxClusterWeight) {
                // weighted average of the two centroids
                centroidMean = centroidMean + (entryMean - centroidMean) * entryWeight / tentativeWeight;
                centroidWeight = tentativeWeight;
            }
            else {
                lastCentroid++;

                weightSoFar += centroidWeight;
                currentQuantile = weightSoFar / totalWeight;
                currentQuantileMaxClusterSize = maxRelativeClusterSize(currentQuantile, normalizer);

                centroidWeight = entryWeight;
                centroidMean = entryMean;
            }

            tempMeans[lastCentroid] = centroidMean;
            tempWeights[lastCentroid] = centroidWeight;
        }

        centroidCount = lastCentroid + 1;

        if (backwards) {
            Doubles.reverse(tempMeans, 0, centroidCount);
            Doubles.reverse(tempWeights, 0, centroidCount);
        }
        backwards = !backwards;

        // the compacted output lives in the temp buffers; swap them in as the live arrays
        swapMergeBuffers();

        // the buffer is now fully sorted (ascending) and compressed
        sortedPrefixLength = centroidCount;
    }

    private void sortCentroids()
    {
        // means[0, sortedPrefixLength) is already ascending. If nothing was appended since the last
        // merge, the whole buffer is already sorted.
        if (sortedPrefixLength >= centroidCount) {
            return;
        }

        // sort only the unsorted tail
        DoubleArrays.sort(means, weights, sortedPrefixLength, centroidCount);

        // merge the sorted prefix run with the just-sorted tail run into a single ascending order
        if (sortedPrefixLength > 0) {
            mergeSortedRuns();
        }
    }

    private void mergeSortedRuns()
    {
        // means[0, sortedPrefixLength) and means[sortedPrefixLength, centroidCount) (with their
        // weights) are each ascending; merge them into the temp buffers, then swap them in.
        int left = 0;
        int right = sortedPrefixLength;
        int out = 0;
        while (left < sortedPrefixLength && right < centroidCount) {
            if (means[left] <= means[right]) {
                tempMeans[out] = means[left];
                tempWeights[out] = weights[left];
                left++;
            }
            else {
                tempMeans[out] = means[right];
                tempWeights[out] = weights[right];
                right++;
            }
            out++;
        }
        while (left < sortedPrefixLength) {
            tempMeans[out] = means[left];
            tempWeights[out] = weights[left];
            left++;
            out++;
        }
        while (right < centroidCount) {
            tempMeans[out] = means[right];
            tempWeights[out] = weights[right];
            right++;
            out++;
        }

        swapMergeBuffers();
    }

    private void swapMergeBuffers()
    {
        // The temp buffers and the live arrays are interchangeable scratch of equal length, so swap
        // references instead of copying the freshly written temp buffers back into means/weights.
        double[] tempMeansSwap = means;
        means = tempMeans;
        tempMeans = tempMeansSwap;

        double[] tempWeightsSwap = weights;
        weights = tempWeights;
        tempWeights = tempWeightsSwap;
    }

    @VisibleForTesting
    void forceMerge()
    {
        merge(internalCompressionFactor(compression));
    }

    @VisibleForTesting
    int getCentroidCount()
    {
        return centroidCount;
    }

    private void mergeIfNeeded(double compression)
    {
        if (needsMerge) {
            merge(compression);
            needsMerge = false;
        }
    }

    private void ensureCapacity(int newSize)
    {
        if (means.length < newSize) {
            means = Arrays.copyOf(means, newSize);
            weights = Arrays.copyOf(weights, newSize);
        }
    }

    private void ensureMergeBuffersCapacity()
    {
        // The temp buffers serve as scratch for both the run merge and the compaction output, so
        // they must hold every centroid currently in means[]/weights[].
        if (tempMeans == null || tempMeans.length < means.length) {
            tempMeans = new double[means.length];
            tempWeights = new double[means.length];
        }
    }

    private static double interpolate(double x, double x0, double y0, double x1, double y1)
    {
        return (x - x0) / (x1 - x0) * (y1 - y0);
    }

    private static double maxRelativeClusterSize(double quantile, double normalizer)
    {
        return quantile * (1 - quantile) / normalizer;
    }

    private static double normalizer(double compression, double weight)
    {
        return compression / (4 * Math.log(weight / compression) + 24);
    }

    private static double internalCompressionFactor(double compression)
    {
        return 2 * compression;
    }
}
