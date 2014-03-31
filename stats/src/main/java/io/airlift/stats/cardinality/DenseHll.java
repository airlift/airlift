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

import io.airlift.slice.BasicSliceInput;
import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.SizeOf;
import io.airlift.slice.Slice;
import org.openjdk.jol.info.ClassLayout;

import javax.annotation.concurrent.NotThreadSafe;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.stats.cardinality.Utils.alpha;
import static io.airlift.stats.cardinality.Utils.computeIndex;
import static io.airlift.stats.cardinality.Utils.computeValue;
import static io.airlift.stats.cardinality.Utils.linearCounting;
import static io.airlift.stats.cardinality.Utils.numberOfBuckets;

@NotThreadSafe
final class DenseHll
        implements HllInstance
{
    private static final double LINEAR_COUNTING_MIN_EMPTY_BUCKETS = 0.4;

    private static final int BITS_PER_BUCKET = 4;
    private static final int MAX_DELTA = (1 << BITS_PER_BUCKET) - 1;
    private static final int BUCKET_MASK = (1 << BITS_PER_BUCKET) - 1;

    private static final int DENSE_INSTANCE_SIZE = ClassLayout.parseClass(DenseHll.class).instanceSize();

    private final byte indexBitLength;
    private byte baseline;
    private short baselineCount;
    private final byte[] deltas;
    private short overflowBucket = -1;
    private byte overflowValue;

    public DenseHll(int indexBitLength)
    {
        checkArgument(indexBitLength >= 1 && indexBitLength <= Short.SIZE, "indexBitLength is out of range");

        int numberOfBuckets = 1 << indexBitLength;

        this.indexBitLength = (byte) indexBitLength;
        baselineCount = (short) numberOfBuckets;
        deltas = new byte[numberOfBuckets * BITS_PER_BUCKET / Byte.SIZE];
    }

    public DenseHll(Slice serialized)
    {
        BasicSliceInput input = serialized.getInput();

        checkArgument(input.readByte() == Format.DENSE_V1.getTag(), "invalid format tag");

        indexBitLength = input.readByte();
        checkArgument(indexBitLength >= 1 && indexBitLength <= Short.SIZE, "indexBitLength is out of range");

        baseline = input.readByte();
        deltas = new byte[numberOfBuckets(indexBitLength) / 2];
        input.readBytes(deltas);
        overflowBucket = input.readShort();
        overflowValue = input.readByte();

        baselineCount = 0;
        for (int i = 0; i < numberOfBuckets(indexBitLength); i++) {
            if (getDelta(i) == 0) {
                baselineCount++;
            }
        }

        checkArgument(!input.isReadable(), "input is too big");
    }

    public static boolean canDeserialize(Slice serialized)
    {
        return serialized.getByte(0) == Format.DENSE_V1.getTag();
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
        return (int) (DENSE_INSTANCE_SIZE + SizeOf.sizeOf(deltas));
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

    public void insert(int index, int value)
    {
        int delta = value - baseline;
        int oldDelta = getDelta(index);

        if (delta <= oldDelta) {
            return;
        }

        if (delta > MAX_DELTA) {
            // replace the current overflow bucket if necessary
            int overflow = delta - MAX_DELTA;
            if (overflow > overflowValue) {
                overflowBucket = (short) index;
                overflowValue = (byte) overflow;
            }
            delta = MAX_DELTA;
        }

        setDelta(index, delta);

        if (oldDelta == 0) {
            --baselineCount;
            adjustBaselineIfNeeded();
        }
    }

    public Slice serialize()
    {
        int size = estimatedSerializedSize();

        return new DynamicSliceOutput(size)
                .appendByte(Format.DENSE_V1.getTag())
                .appendByte(indexBitLength)
                .appendByte(baseline)
                .appendBytes(deltas)
                .appendShort(overflowBucket)
                .appendByte(overflowValue)
                .slice();
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
                SizeOf.SIZE_OF_SHORT + // overflow bucket index
                SizeOf.SIZE_OF_BYTE;
    }

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

    private int getValue(int bucket)
    {
        int result = baseline + getDelta(bucket);
        if (bucket == overflowBucket) {
            result += overflowValue;
        }

        return result;
    }

    private void adjustBaselineIfNeeded()
    {
        while (baselineCount == 0) {
            baseline++;

            for (int bucket = 0; bucket < numberOfBuckets(indexBitLength); ++bucket) {
                if (overflowBucket == bucket && overflowValue > 0) {
                    overflowValue--;
                    continue;
                }

                if (overflowBucket == bucket) {
                    // overflow bucket has a value of 0, so remove it
                    // and adjust the delta
                    overflowBucket = -1;
                }

                // getDelta is guaranteed to return a value greater than zero
                // because baselineCount is zero (i.e., number of deltas with zero value)
                int newDelta = getDelta(bucket) - 1;
                setDelta(bucket, newDelta);

                if (newDelta == 0) {
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
        int newBaseline = Math.max(baseline, other.baseline);
        int newBaselineCount = 0;
        int newOverflowBucket = -1;
        int newOverflowValue = 0;

        int numberOfBuckets = 1 << indexBitLength;
        for (int i = 0; i < numberOfBuckets; i++) {
            int value = Math.max(getValue(i), other.getValue(i));

            int delta = value - newBaseline;
            if (delta == 0) {
                newBaselineCount++;
            }
            else if (delta > MAX_DELTA) {
                int overflow = delta - MAX_DELTA;
                if (overflow > newOverflowValue) {
                    newOverflowBucket = i;
                    newOverflowValue = overflow;
                }

                delta = MAX_DELTA;
            }

            setDelta(i, delta);
        }

        baseline = (byte) newBaseline;
        baselineCount = (short) newBaselineCount;
        overflowBucket = (short) newOverflowBucket;
        overflowValue = (byte) newOverflowValue;

        return this;
    }

    public static int estimatedInMemorySize(int indexBitLength)
    {
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

        if (overflowBucket != -1) {
            checkState(getDelta(overflowBucket) == MAX_DELTA,
                    "delta in bucket %s is less than MAX_DELTA (%s < %s) even though there's an associated overflow entry",
                    overflowBucket, getDelta(overflowBucket), MAX_DELTA);
        }
    }
}
