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
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import io.airlift.slice.BasicSliceInput;
import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.SizeOf;
import io.airlift.slice.Slice;
import org.openjdk.jol.info.ClassLayout;

import javax.annotation.concurrent.NotThreadSafe;

import java.util.Arrays;
import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.stats.cardinality.Utils.computeIndex;
import static io.airlift.stats.cardinality.Utils.linearCounting;
import static io.airlift.stats.cardinality.Utils.numberOfBuckets;
import static io.airlift.stats.cardinality.Utils.numberOfLeadingZeros;

/**
 * Suitable up to index bits <= 13
 */
@NotThreadSafe
class SparseHll
        implements HllInstance
{
    private static final int SPARSE_TAG = 0b1000_0000;
    private static final int VERSION = 0;

    private static final int SPARSE_INSTANCE_SIZE = ClassLayout.parseClass(SparseHll.class).instanceSize();

    private final byte indexBitLength;
    private short numberOfHashes;
    private short[] shortHashes;
    private short numberOfOverflows;
    private short[] overflows;

    public SparseHll(int indexBitLength)
    {
        checkArgument(indexBitLength <= 13, "indexBitLength must be <= 13, actual: %s", indexBitLength);

        this.indexBitLength = (byte) indexBitLength;
        shortHashes = new short[1];
        overflows = new short[0];
    }

    public SparseHll(Slice serialized)
    {
        BasicSliceInput input = serialized.getInput();

        checkArgument(input.readUnsignedByte() == (SPARSE_TAG | VERSION), "Wrong type/version");

        indexBitLength = input.readByte();

        numberOfHashes = input.readShort();
        numberOfOverflows = input.readShort();

        shortHashes = new short[numberOfHashes];
        for (int i = 0; i < numberOfHashes; i++) {
            shortHashes[i] = input.readShort();
        }

        overflows = new short[numberOfOverflows];
        for (int i = 0; i < numberOfOverflows; i++) {
            overflows[i] = input.readShort();
        }
    }

    public static boolean canDeserialize(Slice serialized)
    {
        return serialized.getUnsignedByte(0) == (SPARSE_TAG | VERSION);
    }

    public void insertHash(long hash)
    {
        insertShortHash(hash);
        insertOverflowEntryIfNeeded(hash);
    }

    private void insertOverflowEntryIfNeeded(long hash)
    {
        int zeros = numberOfLeadingZeros(hash, indexBitLength);
        if (zeros > (Short.SIZE - indexBitLength)) {
            int overflowZeros = zeros - (Short.SIZE - indexBitLength) - 1;

            int bucketIndex = computeIndex(hash, indexBitLength);

            // truncate overflow values to fit in (Short.SIZE - indexBitLength) bits.
            // TODO: this introduces a minor error. Document probability and impact of this happening
            short overflowEntry = (short) ((bucketIndex << (Short.SIZE - indexBitLength)) |
                    (overflowZeros & valueMask(indexBitLength)));

            int position = searchOverflow(bucketIndex);
            if (position < 0) {
                // ensure capacity
                if (numberOfOverflows + 1 > overflows.length) {
                    overflows = Arrays.copyOf(overflows, overflows.length + 1);
                }

                // make room to insert
                int insertionPoint = -(position + 1);
                if (insertionPoint < numberOfOverflows) {
                    System.arraycopy(overflows, insertionPoint, overflows, insertionPoint + 1, numberOfOverflows - insertionPoint);
                }

                overflows[insertionPoint] = overflowEntry;
                numberOfOverflows++;
            }
            else {
                // if new value > old value, update overflow
                int oldValue = extractValue(overflows[position]);
                if (overflowZeros > oldValue) {
                    overflows[position] = overflowEntry;
                }
            }
        }
    }

    public void mergeWith(SparseHll other)
    {
        shortHashes = mergeShortHashes(other);
        numberOfHashes = (short) shortHashes.length;

        overflows = mergeOverflows(other);
        numberOfOverflows = (short) overflows.length;
    }

    public DenseHll toDense()
    {
        DenseHll result = new DenseHll(indexBitLength);

        int overflowIndex = 0;
        for (int hashIndex = 0; hashIndex < numberOfHashes; hashIndex++) {
            short shortHash = shortHashes[hashIndex];

            int bucket = extractIndex(indexBitLength, shortHash);
            int zeros = numberOfLeadingZeros(toLongHash(shortHash), indexBitLength);

            // do we need to look at overflows?
            if (zeros > (Short.SIZE - indexBitLength)) {
                zeros = Short.SIZE - indexBitLength;

                while (overflowIndex < numberOfOverflows) {
                    short overflow = overflows[overflowIndex];
                    int overflowBucket = extractIndex(indexBitLength, overflow);

                    if (overflowBucket > bucket) {
                        // don't increment index in this case since we're already past the index we're looking for
                        break;
                    }

                    overflowIndex++;

                    if (overflowBucket == bucket) {
                        // the presence of an overflow bucket indicates a count of at least 1
                        zeros += extractValue(overflow) + 1;
                        break;
                    }
                }
            }

            result.insert(bucket, zeros + 1); // + 1 because HLL stores leading number of zeros + 1
        }
        return result;
    }

    @Override
    public long cardinality()
    {
        // Estimate the cardinality using linear counting over the theoretical 2^Short.SIZE buckets available due
        // to the fact that we're recording the raw 16-bit hashes. This produces much better precision while in
        // the sparse regime
        int totalBuckets = numberOfBuckets(Short.SIZE);
        int zeroBuckets = totalBuckets - numberOfHashes;

        return Math.round(linearCounting(zeroBuckets, totalBuckets));
    }

    @Override
    public int estimatedInMemorySize()
    {
        return (int) (SPARSE_INSTANCE_SIZE +
                SizeOf.sizeOf(overflows) +
                SizeOf.sizeOf(shortHashes));
    }

    @Override
    public int getIndexBitLength()
    {
        return indexBitLength;
    }

    private void insertShortHash(long hash)
    {
        // TODO: investigate whether accumulate, sort and merge results in better performance due to avoiding the shift+insert in every call

        short shortHash = toShortHash(hash);

        int position = searchShortHash(shortHash);

        // add short hash if missing
        if (position < 0) {
            // ensure capacity
            if (numberOfHashes + 1 > shortHashes.length) {
                shortHashes = Arrays.copyOf(shortHashes, shortHashes.length + 10);
            }

            // shift right
            int insertionPoint = -(position + 1);
            if (insertionPoint < numberOfHashes) {
                System.arraycopy(shortHashes, insertionPoint, shortHashes, insertionPoint + 1, numberOfHashes - insertionPoint);
            }

            shortHashes[insertionPoint] = shortHash;
            numberOfHashes++;
        }
    }

    /**
     * Returns a index of the entry if found. Otherwise, it returns -(insertionPoint + 1)
     */
    private int searchOverflow(int bucketIndex)
    {
        int low = 0;
        int high = numberOfOverflows - 1;

        while (low <= high) {
            int middle = (low + high) >>> 1;

            int middleBucketIndex = extractIndex(indexBitLength, overflows[middle]);

            if (bucketIndex > middleBucketIndex) {
                low = middle + 1;
            }
            else if (bucketIndex < middleBucketIndex) {
                high = middle - 1;
            }
            else {
                return middle;
            }
        }

        return -(low + 1); // not found... return insertion point
    }

    /**
     * Returns a index of the entry if found. Otherwise, it returns -(insertionPoint + 1)
     */
    private int searchShortHash(short value)
    {
        int unsignedValue = value & 0xFF_FF;

        int low = 0;
        int high = numberOfHashes - 1;

        while (low <= high) {
            int middle = (low + high) >>> 1;

            int middleEntry = shortHashes[middle] & 0xFF_FF;

            if (unsignedValue > middleEntry) {
                low = middle + 1;
            }
            else if (unsignedValue < middleEntry) {
                high = middle - 1;
            }
            else {
                return middle;
            }
        }

        return -(low + 1); // not found... return insertion point
    }

    private short[] mergeOverflows(SparseHll other)
    {
        short[] result = new short[numberOfOverflows + other.numberOfOverflows];
        int left = 0;
        int right = 0;

        int index = 0;
        while (left < numberOfOverflows && right < other.numberOfOverflows) {
            int leftIndex = extractIndex(indexBitLength, overflows[left]);
            int rightIndex = extractIndex(indexBitLength, other.overflows[right]);

            if (leftIndex < rightIndex) {
                result[index++] = overflows[left++];
            }
            else if (leftIndex > rightIndex) {
                result[index++] = other.overflows[right++];
            }
            else {
                int leftValue = extractValue(overflows[left]);
                int rightValue = extractValue(other.overflows[right]);

                if (leftValue > rightValue) {
                    result[index] = overflows[left];
                }
                else {
                    result[index] = other.overflows[right];
                }

                index++;
                left++;
                right++;
            }
        }

        while (left < numberOfOverflows) {
            result[index++] = overflows[left++];
        }

        while (right < other.numberOfOverflows) {
            result[index++] = other.overflows[right++];
        }

        return Arrays.copyOf(result, index);
    }

    private short[] mergeShortHashes(SparseHll other)
    {
        short[] result = new short[numberOfHashes + other.numberOfHashes];
        int leftIndex = 0;
        int rightIndex = 0;

        int index = 0;
        while (leftIndex < numberOfHashes && rightIndex < other.numberOfHashes) {
            // need to do unsigned comparison
            int left = shortHashes[leftIndex] & 0xFFFF;
            int right = other.shortHashes[rightIndex] & 0xFFFF;

            if (left < right) {
                result[index++] = shortHashes[leftIndex++];
            }
            else if (left > right) {
                result[index++] = other.shortHashes[rightIndex++];
            }
            else {
                result[index] = shortHashes[leftIndex];
                index++;
                leftIndex++;
                rightIndex++;
            }
        }

        while (leftIndex < numberOfHashes) {
            result[index++] = shortHashes[leftIndex++];
        }

        while (rightIndex < other.numberOfHashes) {
            result[index++] = other.shortHashes[rightIndex++];
        }

        return Arrays.copyOf(result, index);
    }

    private short toShortHash(long hash)
    {
        return (short) (hash >>> (Long.SIZE - Short.SIZE));
    }

    /**
     * Turns a short hash back into a long hash (lossy -- all lsb bits will be 0)
     */
    private long toLongHash(short shortHash)
    {
        return ((long) shortHash) << (Long.SIZE - Short.SIZE);
    }

    /**
     * Extracts the bucket index from a 16-bit entry. The index is contained in the most significant indexBitLength bits
     */
    private static int extractIndex(int indexBitLength, short entry)
    {
        return ((entry & 0xFF_FF) >>> (Short.SIZE - indexBitLength));
    }

    /**
     * Extracts the value from a 16-bit entry. The value is contained in the least significant 16-indexLength bits
     */
    private int extractValue(short entry)
    {
        return entry & valueMask(indexBitLength);
    }

    /**
     * Returns the bit mask for extracting the the value from a 16-bit entry.
     */
    private static int valueMask(int indexBitLength)
    {
        return (1 << (Short.SIZE - indexBitLength)) - 1;
    }

    public Slice serialize()
    {
        int size = SizeOf.SIZE_OF_BYTE + // type + version
                SizeOf.SIZE_OF_BYTE + // p
                SizeOf.SIZE_OF_SHORT + // number of short hashes
                SizeOf.SIZE_OF_SHORT + // number of overflow entries
                SizeOf.SIZE_OF_SHORT * numberOfHashes + //
                SizeOf.SIZE_OF_SHORT * numberOfOverflows;

        DynamicSliceOutput out = new DynamicSliceOutput(size)
                .appendByte(SPARSE_TAG | VERSION)
                .appendByte(indexBitLength)
                .appendShort(numberOfHashes)
                .appendShort(numberOfOverflows);

        for (int i = 0; i < numberOfHashes; i++) {
            out.appendShort(shortHashes[i]);
        }

        for (int i = 0; i < numberOfOverflows; i++) {
            out.appendShort(overflows[i]);
        }

        return out.slice();
    }

    @Override
    public int estimatedSerializedSize()
    {
        return SizeOf.SIZE_OF_SHORT // type + version
                + SizeOf.SIZE_OF_BYTE  // p
                + SizeOf.SIZE_OF_SHORT // numberOfHashes
                + SizeOf.SIZE_OF_SHORT // numberOfOverflows
                + numberOfHashes * SizeOf.SIZE_OF_SHORT // hashes
                + numberOfOverflows * SizeOf.SIZE_OF_SHORT; // overflows
    }

    @VisibleForTesting
    public void verify()
    {
        checkState(numberOfHashes <= shortHashes.length,
                "Expected number of hashes (%s) larger than array length (%s)",
                numberOfHashes, shortHashes.length);

        checkState(numberOfOverflows <= overflows.length,
                "Expected number of overflows (%s) larger than array length (%s)",
                numberOfOverflows, overflows.length);

        Comparator<Short> hashComparator = new Comparator<Short>()
        {
            @Override
            public int compare(Short o1, Short o2)
            {
                return Ints.compare(o1.intValue() & 0xFFFF, o2.intValue() & 0xFFFF);
            }
        };

        checkState(Ordering.from(hashComparator).isOrdered(Shorts.asList(Arrays.copyOf(shortHashes, numberOfHashes))),
                "hashes are not sorted");

        Comparator<Short> overflowComparator = new Comparator<Short>()
        {
            @Override
            public int compare(Short o1, Short o2)
            {
                return Ints.compare(extractIndex(indexBitLength, o1), extractIndex(indexBitLength, o2));
            }
        };

        checkState(Ordering.from(overflowComparator).isOrdered(Shorts.asList(Arrays.copyOf(overflows, numberOfOverflows))),
                "overflows are not sorted");

    }
}
