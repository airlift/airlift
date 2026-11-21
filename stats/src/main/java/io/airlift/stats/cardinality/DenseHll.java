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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
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

    // For every possible slot (two packed deltas), the sum of 2^(MAX_DELTA - delta)
    private static final int[] SCALED_SLOT_SUMS = new int[1 << Byte.SIZE];

    static {
        for (int slot = 0; slot < SCALED_SLOT_SUMS.length; slot++) {
            SCALED_SLOT_SUMS[slot] = (1 << (MAX_DELTA - (slot >> BITS_PER_BUCKET))) + (1 << (MAX_DELTA - (slot & BUCKET_MASK)));
        }
    }

    private static final boolean VECTOR_API_ENABLED = ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent();

    private static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final long LOWEST_BIT_OF_EACH_DELTA = 0x1111_1111_1111_1111L;
    private static final long LOWEST_DELTA_OF_EACH_BYTE = 0x0F0F_0F0F_0F0F_0F0FL;
    private static final long LOWEST_BIT_OF_EACH_BYTE = 0x0101_0101_0101_0101L;
    private static final long HIGHEST_BIT_OF_EACH_BYTE = 0x8080_8080_8080_8080L;

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
            throw new IllegalArgumentException("Invalid format tag: %d".formatted(formatTag));
        }

        baselineCount = countZeroDeltas(deltas);

        checkArgument(!input.isReadable(), "input is too big");
    }

    public static boolean canDeserialize(Slice serialized)
    {
        byte formatTag = serialized.getByte(0);
        return formatTag == Format.DENSE_V1.getTag() || formatTag == Format.DENSE_V2.getTag();
    }

    @Override
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

        double sum = sumOfInversePowers();

        double estimate = (alpha(indexBitLength) * numberOfBuckets * numberOfBuckets) / sum;
        estimate = correctBias(estimate);

        return Math.round(estimate);
    }

    /**
     * Returns the sum of 2^-value over all buckets, bit for bit equal to adding the terms one bucket at a time
     */
    @VisibleForTesting
    double sumOfInversePowers()
    {
        // Read as unsigned, so that a (corrupt) negative overflow makes the sum fall back
        int maxOverflow = 0;
        for (int i = 0; i < overflows; i++) {
            maxOverflow = Math.max(maxOverflow, Byte.toUnsignedInt(overflowValues[i]));
        }

        int maxDelta = MAX_DELTA + maxOverflow;
        if (!canSumExactlyAsIntegers(maxDelta)) {
            return sumOfInversePowersSequentially();
        }

        long scaledSum = 0;
        for (byte slot : deltas) {
            scaledSum += SCALED_SLOT_SUMS[Byte.toUnsignedInt(slot)];
        }
        scaledSum <<= maxOverflow;

        for (int i = 0; i < overflows; i++) {
            if (isEffectiveOverflowEntry(i)) {
                // The bucket was added as 2^-MAX_DELTA, but it is 2^-(MAX_DELTA + overflow)
                scaledSum -= (1L << maxOverflow) - (1L << (maxOverflow - overflowValues[i]));
            }
        }

        return (double) scaledSum / (1L << (baseline + maxDelta));
    }

    /**
     * Every term 2^-value is a multiple of 2^-(baseline + maxDelta), so the sum is that unit times an integer
     * of at most numberOfBuckets * 2^maxDelta. If that fits in a double's significand, no addition rounds
     * in any order, and integer and sequential sums are equal.
     */
    private boolean canSumExactlyAsIntegers(int maxDelta)
    {
        // a negative baseline is corrupt; baseline + maxDelta must be a valid shift for the final division
        return baseline >= 0
                && baseline + maxDelta < Long.SIZE - 1
                && indexBitLength + maxDelta <= Double.PRECISION;
    }

    // Same condition under which getValue() adds the overflow of the entry's bucket
    private boolean isEffectiveOverflowEntry(int entry)
    {
        int bucket = overflowBuckets[entry];
        return bucket < numberOfBuckets(indexBitLength) && getDelta(bucket) == MAX_DELTA && findOverflowEntry(bucket) == entry;
    }

    private double sumOfInversePowersSequentially()
    {
        double sum = 0;
        for (int i = 0; i < numberOfBuckets(indexBitLength); i++) {
            int value = getValue(i);
            sum += 1.0 / (1L << value);
        }
        return sum;
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

    @Override
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

    @Override
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

    private static int countZeroDeltas(byte[] deltas)
    {
        int nonZeroDeltas = 0;

        int offset = 0;
        for (; offset <= deltas.length - Long.BYTES; offset += Long.BYTES) {
            long word = (long) LONG_HANDLE.get(deltas, offset);
            // OR the 4 bits of each delta into its lowest bit
            word |= word >>> 2;
            word |= word >>> 1;
            word &= LOWEST_BIT_OF_EACH_DELTA;
            // Count the bits that are set: add up the two in each byte, then all the bytes into the highest one.
            // Long.bitCount(word) does the same, but is slower on aarch64, where it takes four instructions
            // through a vector register.
            word = (word + (word >>> BITS_PER_BUCKET)) & LOWEST_DELTA_OF_EACH_BYTE;
            nonZeroDeltas += (int) ((word * LOWEST_BIT_OF_EACH_BYTE) >>> (Long.SIZE - Byte.SIZE));
        }

        // there are fewer than 8 bytes when indexBitLength is less than 4
        for (; offset < deltas.length; offset++) {
            if ((deltas[offset] & 0xF0) != 0) {
                nonZeroDeltas++;
            }
            if ((deltas[offset] & 0x0F) != 0) {
                nonZeroDeltas++;
            }
        }

        return deltas.length * 2 - nonZeroDeltas;
    }

    private void adjustBaselineIfNeeded()
    {
        while (baselineCount == 0) {
            baseline++;

            // Keep the incubator dependency optional and avoid vector setup for small sketches.
            if (VECTOR_API_ENABLED && deltas.length >= 128) {
                baselineCount = DenseHllVector.adjustBaseline(this, deltas, overflows != 0);
                continue;
            }

            int slot = 0;
            for (; slot <= deltas.length - Long.BYTES; slot += Long.BYTES) {
                long word = (long) LONG_HANDLE.get(deltas, slot);
                if (overflows != 0 && hasMaxDelta(word)) {
                    baselineCount += adjustBaselineScalar(slot * 2, (slot + Long.BYTES) * 2);
                    continue;
                }

                // Every delta is positive when baselineCount is zero, so subtracting one
                // from all sixteen nibbles cannot borrow across bucket boundaries.
                word -= LOWEST_BIT_OF_EACH_DELTA;
                LONG_HANDLE.set(deltas, slot, word);
                baselineCount += countZeroDeltas(word);
            }
            baselineCount += adjustBaselineScalar(slot * 2, deltas.length * 2);
        }
    }

    int adjustBaselineScalar(int fromBucket, int toBucket)
    {
        int baselineCount = 0;
        for (int bucket = fromBucket; bucket < toBucket; bucket++) {
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
        return baselineCount;
    }

    /**
     * Returns "this" for chaining
     */
    public DenseHll mergeWith(DenseHll other)
    {
        if (indexBitLength != other.indexBitLength) {
            throw new IllegalArgumentException("Cannot merge HLLs with different number of buckets: %s vs %s".formatted(
                    numberOfBuckets(indexBitLength),
                    numberOfBuckets(other.indexBitLength)));
        }

        int newBaseline = Math.max(this.baseline, other.baseline);
        int baselineCount = 0;

        // The deltas of the HLL with the lower baseline have to be reduced by the difference between the baselines
        // before they can be compared with the other ones. No delta is larger than MAX_DELTA, so reducing them
        // by more than that makes no difference.
        byte[] higherBaselineDeltas = this.baseline >= other.baseline ? this.deltas : other.deltas;
        byte[] lowerBaselineDeltas = this.baseline >= other.baseline ? other.deltas : this.deltas;
        long reduction = Math.min(Math.abs(this.baseline - other.baseline), MAX_DELTA) * LOWEST_BIT_OF_EACH_BYTE;

        // merge 16 buckets at a time
        int slot = 0;
        for (; slot <= deltas.length - Long.BYTES; slot += Long.BYTES) {
            long higherBaseline = (long) LONG_HANDLE.get(higherBaselineDeltas, slot);
            long lowerBaseline = (long) LONG_HANDLE.get(lowerBaselineDeltas, slot);

            if (hasMaxDelta(higherBaseline) || hasMaxDelta(lowerBaseline)) {
                // Overflow entries are read, added and removed only for buckets that have MAX_DELTA before
                // or after the merge, and a merged delta can be MAX_DELTA only if one of the original ones is
                baselineCount += mergeSlots(other, newBaseline, slot, slot + Long.BYTES);
                continue;
            }

            // The low and the high delta of each byte are merged separately, as that takes a fifth bit
            long lowDeltas = mergeDeltas(higherBaseline & LOWEST_DELTA_OF_EACH_BYTE, lowerBaseline & LOWEST_DELTA_OF_EACH_BYTE, reduction);
            long highDeltas = mergeDeltas((higherBaseline >>> BITS_PER_BUCKET) & LOWEST_DELTA_OF_EACH_BYTE, (lowerBaseline >>> BITS_PER_BUCKET) & LOWEST_DELTA_OF_EACH_BYTE, reduction);
            long merged = lowDeltas | (highDeltas << BITS_PER_BUCKET);

            LONG_HANDLE.set(deltas, slot, merged);
            baselineCount += countZeroDeltas(merged);
        }
        // there are fewer than 8 slots when indexBitLength is less than 4
        baselineCount += mergeSlots(other, newBaseline, slot, deltas.length);

        this.baseline = (byte) newBaseline;
        this.baselineCount = baselineCount;

        // all baseline values in one of the HLLs lost to the values
        // in the other HLL, so we need to adjust the final baseline
        adjustBaselineIfNeeded();

        return this;
    }

    /**
     * Merges the given slots one bucket at a time. Returns the number of merged buckets that are at the new baseline.
     */
    private int mergeSlots(DenseHll other, int newBaseline, int fromSlot, int toSlot)
    {
        int baselineCount = 0;

        int bucket = fromSlot * 2;
        for (int i = fromSlot; i < toSlot; i++) {
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

        return baselineCount;
    }

    /**
     * Returns max(deltas, deltasToReduce - reduction) for each of the 8 bytes. None of the bytes can be larger than MAX_DELTA.
     */
    private static long mergeDeltas(long deltas, long deltasToReduce, long reduction)
    {
        // Setting the highest bit of each byte keeps the subtraction from borrowing from the next byte.
        // The bit is still set afterwards in the bytes where deltasToReduce - reduction is not less than deltas.
        long difference = (deltasToReduce | HIGHEST_BIT_OF_EACH_BYTE) - (deltas + reduction);
        long notLess = difference & HIGHEST_BIT_OF_EACH_BYTE;
        // In those bytes, add the difference, which is in the other 7 bits, to get deltasToReduce - reduction
        return deltas + (difference & (notLess - (notLess >>> (Byte.SIZE - 1))));
    }

    private static boolean hasMaxDelta(long word)
    {
        // AND the 4 bits of each delta into its lowest bit
        word &= word >>> 2;
        word &= word >>> 1;
        return (word & LOWEST_BIT_OF_EACH_DELTA) != 0;
    }

    private static int countZeroDeltas(long word)
    {
        // OR the 4 bits of each delta into its lowest bit
        word |= word >>> 2;
        word |= word >>> 1;
        word &= LOWEST_BIT_OF_EACH_DELTA;
        return Long.SIZE / BITS_PER_BUCKET - Long.bitCount(word);
    }

    /**
     * Returns "this" for chaining
     */
    public DenseHll mergeWith(SparseHll other)
    {
        if (indexBitLength != other.getIndexBitLength()) {
            throw new IllegalArgumentException("Cannot merge HLLs with different number of buckets: %s vs %s".formatted(
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

        checkState(zeroDeltas == baselineCount,
                "baselineCount (%s) doesn't match number of zero deltas (%s)",
                baselineCount,
                zeroDeltas);

        Set<Integer> overflows = new HashSet<>();
        for (int i = 0; i < this.overflows; i++) {
            int bucket = overflowBuckets[i];
            overflows.add(bucket);

            checkState(overflowValues[i] > 0, "Overflow at %s for bucket %s is 0", i, bucket);
            checkState(getDelta(bucket) == MAX_DELTA,
                    "delta in bucket %s is less than MAX_DELTA (%s < %s) even though there's an associated overflow entry",
                    bucket,
                    getDelta(bucket),
                    MAX_DELTA);
        }

        checkState(overflows.size() == this.overflows,
                "Duplicate overflow buckets: %s",
                Ints.asList(Arrays.copyOf(overflowBuckets, this.overflows)));
    }
}
