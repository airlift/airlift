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
package io.airlift.stats.cardinality;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import io.airlift.slice.BasicSliceInput;
import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.SizeOf;
import io.airlift.slice.Slice;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.slice.SizeOf.instanceSize;
import static io.airlift.stats.cardinality.Utils.alpha;
import static io.airlift.stats.cardinality.Utils.computeIndex;
import static io.airlift.stats.cardinality.Utils.computeValue;
import static io.airlift.stats.cardinality.Utils.linearCounting;
import static io.airlift.stats.cardinality.Utils.numberOfBuckets;

/**
 * This class is NOT thread safe.
 */
final class DenseHll
        implements HllInstance
{
    private static final double LINEAR_COUNTING_MIN_EMPTY_BUCKETS = 0.4;

    private static final int BITS_PER_BUCKET = 4;
    private static final int MAX_DELTA = (1 << BITS_PER_BUCKET) - 1;
    private static final int BUCKET_MASK = (1 << BITS_PER_BUCKET) - 1;

    private static final int DENSE_INSTANCE_SIZE = instanceSize(DenseHll.class);
    private static final int OVERFLOW_GROW_INCREMENT = 5;

    private final byte indexBitLength;
    private byte baseline;
    private int baselineCount;
    private final byte[] deltas;

    private int overflows;
    private int[] overflowBuckets;
    private byte[] overflowValues;

    public DenseHll(int indexBitLength)
    {
        validatePrefixLength(indexBitLength);

        int numberOfBuckets = numberOfBuckets(indexBitLength);

        this.indexBitLength = (byte) indexBitLength;
        baselineCount = numberOfBuckets;
        deltas = new byte[numberOfBuckets * BITS_PER_BUCKET / Byte.SIZE];
        overflowBuckets = new int[0];
        overflowValues = new byte[0];
    }

    public DenseHll(Slice serialized)
    {
        BasicSliceInput input = serialized.getInput();

        byte formatTag = input.readByte();
        checkArgument(formatTag == Format.DENSE_V1.getTag() || formatTag == Format.DENSE_V2.getTag(), "Invalid format tag");

        indexBitLength = input.readByte();
        validatePrefixLength(indexBitLength);
        int numberOfBuckets = numberOfBuckets(indexBitLength);

        baseline = input.readByte();
        deltas = new byte[numberOfBuckets / 2];
        input.readBytes(deltas);

        if (formatTag == Format.DENSE_V1.getTag()) {
            // for backward compatibility
            int bucket = input.readShort();
            byte value = input.readByte();
            if (bucket >= 0 && value > 0) {
                checkArgument(bucket <= numberOfBuckets, "Overflow bucket index is out of range");
                overflows = 1;
                overflowBuckets = new int[] {bucket};
                overflowValues = new byte[] {value};
            }
            else {
                overflows = 0;
                overflowBuckets = new int[0];
                overflowValues = new byte[0];
            }
        }
        else if (formatTag == Format.DENSE_V2.getTag()) {
            overflows = input.readUnsignedShort();
            checkArgument(overflows <= numberOfBuckets, "Overflow entries is greater than actual number of buckets (possibly corrupt input)");

            overflowBuckets = new int[overflows];
            overflowValues = new byte[overflows];

            for (int i = 0; i < overflows; i++) {
                overflowBuckets[i] = input.readUnsignedShort();
                checkArgument(overflowBuckets[i] <= numberOfBuckets, "Overflow bucket index is out of range");
            }

            for (int i = 0; i < overflows; i++) {
                overflowValues[i] = input.readByte();
                checkArgument(overflowValues[i] > 0, "Overflow bucket value must be > 0");
            }
        }
        else {
            throw new IllegalArgumentException(String.format("Invalid format tag: %d", formatTag));
        }

        baselineCount = 0;
        for (int i = 0; i < numberOfBuckets; i++) {
            if (getDelta(i) == 0) {
                baselineCount++;
            }
        }

        checkArgument(!input.isReadable(), "input is too big");
    }

    public static boolean canDeserialize(Slice serialized)
    {
        byte formatTag = serialized.getByte(0);
        return formatTag == Format.DENSE_V1.getTag() || formatTag == Format.DENSE_V2.getTag();
    }

    public void insertHash(long hash)
    {
        int index = computeIndex(hash, indexBitLength);
        int value = computeValue(hash, indexBitLength);

        insert(index, value);
    }

    @Override
    public int estimatedInMemorySize()
    {
        return (int) (DENSE_INSTANCE_SIZE +
                SizeOf.sizeOf(deltas) +
                SizeOf.sizeOf(overflowBuckets) +
                SizeOf.sizeOf(overflowValues));
    }

    @Override
    public int getIndexBitLength()
    {
        return indexBitLength;
    }

    @Override
    public long cardinality()
    {
        int numberOfBuckets = numberOfBuckets(indexBitLength);

        // if baseline is zero, then baselineCount is the number of buckets with value 0
        if ((baseline == 0) && (baselineCount > (LINEAR_COUNTING_MIN_EMPTY_BUCKETS * numberOfBuckets))) {
            return Math.round(linearCounting(baselineCount, numberOfBuckets));
        }

        double sum = 0;
        for (int i = 0; i < numberOfBuckets; i++) {
            int value = getValue(i);
            sum += 1.0 / (1L << value);
        }

        double estimate = (alpha(indexBitLength) * numberOfBuckets * numberOfBuckets) / sum;
        estimate = correctBias(estimate);

        return Math.round(estimate);
    }

    private double correctBias(double rawEstimate)
    {
        double[] estimates = BiasCorrection.RAW_ESTIMATES[indexBitLength - 4];
        if (rawEstimate < estimates[0] || rawEstimate > estimates[estimates.length - 1]) {
            return rawEstimate;
        }

        double[] biases = BiasCorrection.BIAS[indexBitLength - 4];

        int position = search(rawEstimate, estimates);

        double bias;
        if (position >= 0) {
            bias = biases[position];
        }
        else {
            // interpolate
            int insertionPoint = -(position + 1);

            double x0 = estimates[insertionPoint - 1];
            double y0 = biases[insertionPoint - 1];
            double x1 = estimates[insertionPoint];
            double y1 = biases[insertionPoint];

            bias = ((((rawEstimate - x0) * (y1 - y0)) / (x1 - x0)) + y0);
        }

        return rawEstimate - bias;
    }

    private int search(double rawEstimate, double[] estimateCurve)
    {
        int low = 0;
        int high = estimateCurve.length - 1;

        while (low <= high) {
            int middle = (low + high) >>> 1;

            double middleValue = estimateCurve[middle];

            if (rawEstimate > middleValue) {
                low = middle + 1;
            }
            else if (rawEstimate < middleValue) {
                high = middle - 1;
            }
            else {
                return middle;
            }
        }

        return -(low + 1);
    }

    public void insert(int bucket, int value)
    {
        int delta = value - baseline;
        final int oldDelta = getDelta(bucket);

        if (delta <= oldDelta || (oldDelta == MAX_DELTA && (delta <= oldDelta + getOverflow(bucket)))) {
            // the old bucket value is (baseline + oldDelta) + possibly an overflow, so it's guaranteed to be >= the new value
            return;
        }

        if (delta > MAX_DELTA) {
            byte overflow = (byte) (delta - MAX_DELTA);

            int overflowEntry = findOverflowEntry(bucket);
            if (overflowEntry != -1) {
                setOverflow(overflowEntry, overflow);
            }
            else {
                addOverflow(bucket, overflow);
            }

            delta = MAX_DELTA;
        }

        setDelta(bucket, delta);

        if (oldDelta == 0) {
            --baselineCount;
            adjustBaselineIfNeeded();
        }
    }

    public Slice serialize()
    {
        int size = estimatedSerializedSize();

        DynamicSliceOutput output = new DynamicSliceOutput(size)
                .appendByte(Format.DENSE_V2.getTag())
                .appendByte(indexBitLength)
                .appendByte(baseline)
                .appendBytes(deltas)
                .appendShort(overflows);

        // sort overflow arrays to get consistent serialization for equivalent HLLs
        sortOverflows();

        for (int i = 0; i < overflows; i++) {
            output.appendShort(overflowBuckets[i]);
        }
        for (int i = 0; i < overflows; i++) {
            output.appendByte(overflowValues[i]);
        }

        return output.slice();
    }

    private void sortOverflows()
    {
        // traditional insertion sort (ok for small arrays)
        for (int i = 1; i < overflows; i++) {
            for (int j = i; j > 0 && overflowBuckets[j - 1] > overflowBuckets[j]; j--) {
                int bucket = overflowBuckets[j];
                int value = overflowValues[j];

                overflowBuckets[j] = overflowBuckets[j - 1];
                overflowValues[j] = overflowValues[j - 1];

                overflowBuckets[j - 1] = bucket;
                overflowValues[j - 1] = (byte) value;
            }
        }
    }

    @Override
    public DenseHll toDense()
    {
        return this;
    }

    public int estimatedSerializedSize()
    {
        return SizeOf.SIZE_OF_BYTE + // type + version
                SizeOf.SIZE_OF_BYTE + // p
                SizeOf.SIZE_OF_BYTE + // baseline
                (numberOfBuckets(indexBitLength) * SizeOf.SIZE_OF_BYTE) / 2 + // buckets
                SizeOf.SIZE_OF_SHORT + // overflow bucket count
                SizeOf.SIZE_OF_SHORT * overflows + // overflow bucket indexes
                SizeOf.SIZE_OF_BYTE * overflows; // overflow bucket values
    }

    @SuppressWarnings("NarrowingCompoundAssignment")
    private void setDelta(int bucket, int value)
    {
        int slot = bucketToSlot(bucket);

        // clear the old value
        byte clearMask = (byte) (BUCKET_MASK << shiftForBucket(bucket));
        deltas[slot] &= ~clearMask;

        // set the new value
        byte setMask = (byte) (value << shiftForBucket(bucket));
        deltas[slot] |= setMask;
    }

    private int getDelta(int bucket)
    {
        int slot = bucketToSlot(bucket);

        return (deltas[slot] >> shiftForBucket(bucket)) & BUCKET_MASK;
    }

    @VisibleForTesting
    int getValue(int bucket)
    {
        int delta = getDelta(bucket);

        if (delta == MAX_DELTA) {
            delta += getOverflow(bucket);
        }

        return baseline + delta;
    }

    private void adjustBaselineIfNeeded()
    {
        while (baselineCount == 0) {
            baseline++;

            for (int bucket = 0; bucket < numberOfBuckets(indexBitLength); ++bucket) {
                int delta = getDelta(bucket);

                boolean hasOverflow = false;
                if (delta == MAX_DELTA) {
                    // scan overflows
                    for (int i = 0; i < overflows; i++) {
                        if (overflowBuckets[i] == bucket) {
                            hasOverflow = true;
                            overflowValues[i]--;

                            if (overflowValues[i] == 0) {
                                int lastEntry = overflows - 1;
                                if (i < lastEntry) {
                                    // remove the entry by moving the last entry to this position
                                    overflowBuckets[i] = overflowBuckets[lastEntry];
                                    overflowValues[i] = overflowValues[lastEntry];

                                    // clean up to make it easier to catch bugs
                                    overflowBuckets[lastEntry] = -1;
                                    overflowValues[lastEntry] = 0;
                                }
                                overflows--;
                            }
                            break;
                        }
                    }
                }

                if (!hasOverflow) {
                    // getDelta is guaranteed to return a value greater than zero
                    // because baselineCount is zero (i.e., number of deltas with zero value)
                    // So it's safe to decrement here
                    delta--;
                    setDelta(bucket, delta);
                }

                if (delta == 0) {
                    ++baselineCount;
                }
            }
        }
    }

    /**
     * Returns "this" for chaining
     */
    public DenseHll mergeWith(DenseHll other)
    {
        if (indexBitLength != other.indexBitLength) {
            throw new IllegalArgumentException(String.format(
                    "Cannot merge HLLs with different number of buckets: %s vs %s",
                    numberOfBuckets(indexBitLength),
                    numberOfBuckets(other.indexBitLength)));
        }

        int newBaseline = Math.max(this.baseline, other.baseline);
        int baselineCount = 0;

        int bucket = 0;
        for (int i = 0; i < deltas.length; i++) {
            int newSlot = 0;

            byte slot1 = deltas[i];
            byte slot2 = other.deltas[i];

            for (int shift = 4; shift >= 0; shift -= 4) {
                int delta1 = (slot1 >>> shift) & 0b1111;
                int delta2 = (slot2 >>> shift) & 0b1111;

                int value1 = this.baseline + delta1;
                int value2 = other.baseline + delta2;

                int overflowEntry = -1;
                if (delta1 == MAX_DELTA) {
                    overflowEntry = findOverflowEntry(bucket);
                    if (overflowEntry != -1) {
                        value1 += overflowValues[overflowEntry];
                    }
                }

                if (delta2 == MAX_DELTA) {
                    value2 += other.getOverflow(bucket);
                }

                int newValue = Math.max(value1, value2);
                int newDelta = newValue - newBaseline;

                if (newDelta == 0) {
                    baselineCount++;
                }

                newDelta = updateOverflow(bucket, overflowEntry, newDelta);

                newSlot <<= 4;
                newSlot |= newDelta;
                bucket++;
            }

            this.deltas[i] = (byte) newSlot;
        }

        this.baseline = (byte) newBaseline;
        this.baselineCount = baselineCount;

        // all baseline values in one of the HLLs lost to the values
        // in the other HLL, so we need to adjust the final baseline
        adjustBaselineIfNeeded();

        return this;
    }

    /**
     * Returns "this" for chaining
     */
    public DenseHll mergeWith(SparseHll other)
    {
        if (indexBitLength != other.getIndexBitLength()) {
            throw new IllegalArgumentException(String.format(
                    "Cannot merge HLLs with different number of buckets: %s vs %s",
                    numberOfBuckets(indexBitLength),
                    numberOfBuckets(other.getIndexBitLength())));
        }

        other.eachBucket(this::insert);

        return this;
    }

    private int findOverflowEntry(int bucket)
    {
        for (int i = 0; i < overflows; i++) {
            if (overflowBuckets[i] == bucket) {
                return i;
            }
        }
        return -1;
    }

    private int getOverflow(int bucket)
    {
        for (int i = 0; i < overflows; i++) {
            if (overflowBuckets[i] == bucket) {
                return overflowValues[i];
            }
        }
        return 0;
    }

    private int updateOverflow(int bucket, int overflowEntry, int delta)
    {
        if (delta > MAX_DELTA) {
            if (overflowEntry != -1) {
                // update existing overflow
                setOverflow(overflowEntry, (byte) (delta - MAX_DELTA));
            }
            else {
                addOverflow(bucket, (byte) (delta - MAX_DELTA));
            }
            delta = MAX_DELTA;
        }
        else if (overflowEntry != -1) {
            removeOverflow(overflowEntry);
        }

        return delta;
    }

    private void setOverflow(int overflowEntry, byte overflow)
    {
        overflowValues[overflowEntry] = overflow;
    }

    private void removeOverflow(int overflowEntry)
    {
        // remove existing overflow
        overflowBuckets[overflowEntry] = overflowBuckets[overflows - 1];
        overflowValues[overflowEntry] = overflowValues[overflows - 1];
        overflows--;
    }

    private void addOverflow(int bucket, byte overflow)
    {
        // add new delta
        overflowBuckets = Ints.ensureCapacity(overflowBuckets, overflows + 1, OVERFLOW_GROW_INCREMENT);
        overflowValues = Bytes.ensureCapacity(overflowValues, overflows + 1, OVERFLOW_GROW_INCREMENT);

        overflowBuckets[overflows] = bucket;
        overflowValues[overflows] = overflow;

        overflows++;
    }

    public static int estimatedInMemorySize(int indexBitLength)
    {
        // note: we don't take into account overflow entries since their number can vary
        return (int) (DENSE_INSTANCE_SIZE + SizeOf.sizeOfByteArray(numberOfBuckets(indexBitLength) / 2));
    }

    private static int bucketToSlot(int bucket)
    {
        return bucket >> 1;
    }

    private static int shiftForBucket(int bucket)
    {
        // ((1 - bucket) % 2) * BITS_PER_BUCKET
        return ((~bucket) & 1) << 2;
    }

    private static void validatePrefixLength(int indexBitLength)
    {
        checkArgument(indexBitLength >= 1 && indexBitLength <= 16, "indexBitLength is out of range");
    }

    @Override
    public void verify()
    {
        int zeroDeltas = 0;
        for (int i = 0; i < numberOfBuckets(indexBitLength); i++) {
            if (getDelta(i) == 0) {
                zeroDeltas++;
            }
        }

        checkState(zeroDeltas == baselineCount, "baselineCount (%s) doesn't match number of zero deltas (%s)",
                baselineCount, zeroDeltas);

        Set<Integer> overflows = new HashSet<>();
        for (int i = 0; i < this.overflows; i++) {
            int bucket = overflowBuckets[i];
            overflows.add(bucket);

            checkState(overflowValues[i] > 0, "Overflow at %s for bucket %s is 0", i, bucket);
            checkState(getDelta(bucket) == MAX_DELTA,
                    "delta in bucket %s is less than MAX_DELTA (%s < %s) even though there's an associated overflow entry",
                    bucket, getDelta(bucket), MAX_DELTA);
        }

        checkState(overflows.size() == this.overflows, "Duplicate overflow buckets: %s",
                Ints.asList(Arrays.copyOf(overflowBuckets, this.overflows)));
    }
}
