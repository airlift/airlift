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
import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import io.airlift.slice.SizeOf;
import io.airlift.slice.Slice;
import io.airlift.slice.SliceInput;
import io.airlift.slice.SliceOutput;
import io.airlift.slice.Slices;
import org.openjdk.jol.info.ClassLayout;

import javax.annotation.concurrent.NotThreadSafe;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Double.isInfinite;
import static java.lang.Double.isNaN;
import static java.util.Objects.requireNonNull;

@NotThreadSafe
public class TDigest
{
    public static final double DEFAULT_COMPRESSION = 100;

    private static final int FORMAT_TAG = 0;
    private static final int T_DIGEST_SIZE = ClassLayout.parseClass(TDigest.class).instanceSize();
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

    private int[] indexes;
    private double[] tempMeans;
    private double[] tempWeights;

    public TDigest()
    {
        this(DEFAULT_COMPRESSION);
    }

    public TDigest(double compression)
    {
        this(
                compression,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                0,
                0,
                new double[INITIAL_CAPACITY],
                new double[INITIAL_CAPACITY],
                false,
                false);
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
            boolean backwards)
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
                other.backwards);
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
                false);
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
        checkArgument(!isNaN(value), "value is NaN");
        checkArgument(!isNaN(weight), "weight is NaN");
        checkArgument(!isInfinite(value), "value must be finite");
        checkArgument(!isInfinite(weight), "weight must be finite");

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
        min = Math.min(value, min);
        max = Math.max(value, max);

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

    public double valueAt(double quantile)
    {
        checkArgument(quantile >= 0 && quantile <= 1, "quantile should be in [0, 1] range");

        if (centroidCount == 0) {
            return Double.NaN;
        }

        mergeIfNeeded(internalCompressionFactor(compression));

        if (centroidCount == 1) {
            return means[0];
        }

        // offset into the theoretical sequence of all values
        double offset = quantile * totalWeight;

        if (offset < 1) {
            return min;
        }

        if (offset > totalWeight - 1) {
            return max;
        }

        // between bottom and first centroid
        if (weights[0] > 1 && offset < weights[0] / 2) {
            return min + interpolate(offset, 1, min, weights[0] / 2, means[0]);
        }

        // between last centroid and top
        if (weights[centroidCount - 1] > 1 && totalWeight - offset <= weights[centroidCount - 1] / 2) {
            // we interpolate back from the end, so the value is negative
            return max + interpolate(totalWeight - offset, 1, max, weights[centroidCount - 1] / 2, means[centroidCount - 1]);
        }

        double weightSoFar = weights[0] / 2;
        for (int i = 0; i < centroidCount - 1; i++) {
            double delta = (weights[i] + weights[i + 1]) / 2;
            if (weightSoFar + delta >= offset) {
                // single-sample cluster and the quantile falls within that cluster
                if (weights[i] == 1 && offset - weightSoFar < weights[i] / 2) {
                    return means[i];
                }

                // single-sample cluster and the quantile falls within that cluster
                if (weights[i + 1] == 1 && offset - weightSoFar >= weights[i] / 2) {
                    return means[i + 1];
                }

                // At this point, at most one cluster has a single sample
                // If either has a single sample, we exclude its weight
                if (weights[i] == 1) {
                    weightSoFar += weights[i] / 2;
                    delta = weights[i + 1] / 2;
                }
                else if (weights[i + 1] == 1) {
                    delta = weights[i] / 2;
                }

                return means[i] + interpolate(offset - weightSoFar, 0, means[i], delta, means[i + 1]);
            }

            weightSoFar += delta;
        }

        // Should never reach here. We handled the case of offset being
        // between the last centroid and the top above
        throw new AssertionError();
    }

    public List<Double> valuesAt(List<Double> quantiles)
    {
        checkArgument(Ordering.natural().isOrdered(quantiles), "quantiles must be sorted in increasing order");

        // TODO: optimize and compute in a single pass
        return quantiles.stream()
                .map(this::valueAt)
                .collect(Collectors.toList());
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
                SizeOf.sizeOf(tempWeights) +
                SizeOf.sizeOf(indexes));
    }

    private void merge(double compression)
    {
        if (centroidCount == 0) {
            return;
        }

        initializeIndexes();

        DoubleArrays.quickSortIndirect(indexes, means, 0, centroidCount);
        if (backwards) {
            Ints.reverse(indexes, 0, centroidCount);
        }

        double centroidMean = means[indexes[0]];
        double centroidWeight = weights[indexes[0]];

        if (tempMeans == null) {
            tempMeans = new double[INITIAL_CAPACITY];
            tempWeights = new double[INITIAL_CAPACITY];
        }

        int lastCentroid = 0;
        tempMeans[lastCentroid] = centroidMean;
        tempWeights[lastCentroid] = centroidWeight;

        double weightSoFar = 0;
        double normalizer = normalizer(compression, totalWeight);
        double currentQuantile = 0;
        double currentQuantileMaxClusterSize = maxRelativeClusterSize(currentQuantile, normalizer);

        for (int i = 1; i < centroidCount; i++) {
            int index = indexes[i];
            double entryWeight = weights[index];
            double entryMean = means[index];

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

            ensureTempCapacity(lastCentroid);
            tempMeans[lastCentroid] = centroidMean;
            tempWeights[lastCentroid] = centroidWeight;
        }

        centroidCount = lastCentroid + 1;

        if (backwards) {
            Doubles.reverse(tempMeans, 0, centroidCount);
            Doubles.reverse(tempWeights, 0, centroidCount);
        }
        backwards = !backwards;

        System.arraycopy(tempMeans, 0, means, 0, centroidCount);
        System.arraycopy(tempWeights, 0, weights, 0, centroidCount);
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
        }
    }

    private void ensureCapacity(int newSize)
    {
        if (means.length < newSize) {
            means = Arrays.copyOf(means, newSize);
            weights = Arrays.copyOf(weights, newSize);
        }
    }

    private void ensureTempCapacity(int capacity)
    {
        if (tempMeans.length <= capacity) {
            int newSize = capacity + (int) Math.ceil(capacity * 0.5);
            tempMeans = Arrays.copyOf(tempMeans, newSize);
            tempWeights = Arrays.copyOf(tempWeights, newSize);
        }
    }

    private void initializeIndexes()
    {
        if (indexes == null || indexes.length != means.length) {
            indexes = new int[means.length];
        }
        for (int i = 0; i < centroidCount; i++) {
            indexes[i] = i;
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
