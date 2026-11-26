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

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.Slice;
import io.airlift.slice.XxHash64;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;
import static io.airlift.stats.cardinality.TestUtils.sequence;
import static io.airlift.stats.cardinality.Utils.numberOfBuckets;
import static org.assertj.core.api.Assertions.assertThat;

public class TestDenseHll
{
    @Test
    public void testMultipleMerges()
    {
        for (int prefixBitLength : prefixLengths()) {
            DenseHll single = new DenseHll(prefixBitLength);
            DenseHll merged = new DenseHll(prefixBitLength);

            DenseHll current = new DenseHll(prefixBitLength);

            for (int i = 0; i < 10_000_000; i++) {
                if (i % 10_000 == 0) {
                    merged.mergeWith(current);
                    current = new DenseHll(prefixBitLength);
                }

                long hash = XxHash64.hash(i);
                current.insertHash(hash);
                single.insertHash(hash);
            }

            merged.mergeWith(current);

            for (int i = 0; i < numberOfBuckets(prefixBitLength); i++) {
                assertThat(single.getValue(i)).isEqualTo(merged.getValue(i));
            }

            assertThat(single.cardinality()).isEqualTo(merged.cardinality());
        }
    }

    @Test
    public void testHighCardinality()
    {
        for (int prefixBitLength : prefixLengths()) {
            TestingHll testingHll = new TestingHll(prefixBitLength);
            DenseHll hll = new DenseHll(prefixBitLength);
            for (int i = 0; i < 10_000_000; i++) {
                long hash = XxHash64.hash(i);

                testingHll.insertHash(hash);
                hll.insertHash(hash);
            }

            assertSameBuckets(testingHll, hll);
        }
    }

    @Test
    public void testInsert()
    {
        for (int prefixBitLength : prefixLengths()) {
            TestingHll testingHll = new TestingHll(prefixBitLength);
            DenseHll hll = new DenseHll(prefixBitLength);
            for (int i = 0; i < 20_000; i++) {
                long hash = XxHash64.hash(i);

                testingHll.insertHash(hash);
                hll.insertHash(hash);
                hll.verify();
            }

            assertSameBuckets(testingHll, hll);
        }
    }

    @Test
    public void testMergeWithOverflows()
    {
        TestingHll testingHll = new TestingHll(12);
        DenseHll hll1 = new DenseHll(12);
        DenseHll hll2 = new DenseHll(12);

        // these two numbers cause overflows
        long hash1 = XxHash64.hash(25130);
        long hash2 = XxHash64.hash(227291);

        hll1.insertHash(hash1);
        testingHll.insertHash(hash1);

        hll2.insertHash(hash2);
        testingHll.insertHash(hash2);

        hll1.mergeWith(hll2);
        hll1.verify();

        assertSameBuckets(testingHll, hll1);
    }

    @Test
    public void testMerge()
    {
        for (int prefixBitLength : prefixLengths()) {
            // small, non-overlapping
            verifyMerge(prefixBitLength, sequence(0, 100), sequence(100, 200));
            verifyMerge(prefixBitLength, sequence(100, 200), sequence(0, 100));

            // small, overlapping
            verifyMerge(prefixBitLength, sequence(0, 100), sequence(50, 150));
            verifyMerge(prefixBitLength, sequence(50, 150), sequence(0, 100));

            // small, same
            verifyMerge(prefixBitLength, sequence(0, 100), sequence(0, 100));

            // large, non-overlapping
            verifyMerge(prefixBitLength, sequence(0, 20000), sequence(20000, 40000));
            verifyMerge(prefixBitLength, sequence(20000, 40000), sequence(0, 20000));

            // large, overlapping
            verifyMerge(prefixBitLength, sequence(0, 2_000_000), sequence(1_000_000, 3_000_000));
            verifyMerge(prefixBitLength, sequence(1_000_000, 3_000_000), sequence(0, 2_000_000));

            // large, same
            verifyMerge(prefixBitLength, sequence(0, 2_000_000), sequence(0, 2_000_000));
        }
    }

    @Test
    public void testCardinalityMatchesPreviousImplementation()
    {
        // expected values were produced by the bucket-by-bucket implementation
        assertCardinality(11, 100_000, 102_875);
        assertCardinality(11, 3_000_000, 3_016_312);
        assertCardinality(12, 100_000, 100_813);
        assertCardinality(12, 3_000_000, 3_034_206);
        assertCardinality(16, 100_000, 99_920);
        assertCardinality(16, 3_000_000, 2_981_240);
    }

    private static void assertCardinality(int indexBitLength, int values, long expected)
    {
        DenseHll hll = new DenseHll(indexBitLength);
        for (int i = 0; i < values; i++) {
            hll.insertHash(XxHash64.hash(i));
        }
        assertThat(hll.cardinality()).isEqualTo(expected);
    }

    @Test
    public void testSumOfInversePowersWithHashes()
    {
        Random random = new Random(1);
        for (int indexBitLength = 1; indexBitLength <= 16; indexBitLength++) {
            for (int valuesPerBucket : new int[] {1, 3, 20, 200}) {
                DenseHll hll = new DenseHll(indexBitLength);
                for (int i = 0; i < valuesPerBucket << indexBitLength; i++) {
                    hll.insertHash(random.nextLong());
                }
                assertSumOfInversePowers(hll);
            }
        }
    }

    @Test
    public void testSumOfInversePowersWithPackedValues()
    {
        // Include arrays shorter than an unrolled group and every packed pair of deltas.
        for (int indexBitLength : new int[] {1, 2, 3, 4, 12}) {
            for (int value = 0; value < 256; value++) {
                byte[] deltas = new byte[numberOfBuckets(indexBitLength) / 2];
                for (int slot = 0; slot < deltas.length; slot++) {
                    deltas[slot] = (byte) (value + 31 * slot);
                }
                assertSumOfInversePowers(deserialize(indexBitLength, 0, deltas, new int[0], new int[0]));
            }
        }

        // The maximum scaled sum is 2^31 and must not overflow a signed int.
        DenseHll empty = new DenseHll(16);
        assertThat(empty.sumOfInversePowers()).isEqualTo(65536.0);
        assertSumOfInversePowers(empty);
    }

    @Test
    public void testSumOfInversePowersWithOverflows()
    {
        Random random = new Random(2);
        for (int iteration = 0; iteration < 2_000; iteration++) {
            int indexBitLength = 1 + random.nextInt(16);
            int numberOfBuckets = numberOfBuckets(indexBitLength);

            byte[] deltas = new byte[numberOfBuckets / 2];
            random.nextBytes(deltas);

            // overflows as large as 38 - indexBitLength can be summed exactly
            int maxOverflow = 1 + random.nextInt(45);
            int[] overflowBuckets = random.ints(0, numberOfBuckets).distinct().limit(random.nextInt(Math.min(numberOfBuckets, 8) + 1)).toArray();
            int[] overflowValues = new int[overflowBuckets.length];
            for (int i = 0; i < overflowBuckets.length; i++) {
                int bucket = overflowBuckets[i];
                deltas[bucket / 2] |= (byte) (bucket % 2 == 0 ? 0xF0 : 0x0F);
                overflowValues[i] = 1 + random.nextInt(maxOverflow);
            }

            DenseHll hll = deserialize(indexBitLength, random.nextInt(40), deltas, overflowBuckets, overflowValues);
            hll.verify();
            assertSumOfInversePowers(hll);
        }
    }

    @Test
    public void testSumOfInversePowersWithCorruptOverflows()
    {
        Random random = new Random(3);
        for (int iteration = 0; iteration < 2_000; iteration++) {
            int indexBitLength = 1 + random.nextInt(16);
            int numberOfBuckets = numberOfBuckets(indexBitLength);

            byte[] deltas = new byte[numberOfBuckets / 2];
            for (int i = 0; i < deltas.length; i++) {
                int high = random.nextInt(4) == 0 ? 15 : random.nextInt(16);
                int low = random.nextInt(4) == 0 ? 15 : random.nextInt(16);
                deltas[i] = (byte) (high << 4 | low);
            }

            // entries may repeat a bucket, point to a bucket whose delta is not 15, or to the bucket past the last one
            int maxOverflow = 1 + random.nextInt(random.nextInt(8) == 0 ? 127 : 30);
            int[] overflowBuckets = new int[random.nextInt(Math.min(numberOfBuckets, 40) + 1)];
            int[] overflowValues = new int[overflowBuckets.length];
            for (int i = 0; i < overflowBuckets.length; i++) {
                overflowBuckets[i] = random.nextInt(Math.min(numberOfBuckets + 1, 1 << 16));
                overflowValues[i] = 1 + random.nextInt(maxOverflow);
            }

            int baseline = random.nextInt(8) == 0 ? (byte) random.nextInt() : random.nextInt(30);
            assertSumOfInversePowers(deserialize(indexBitLength, baseline, deltas, overflowBuckets, overflowValues));
        }
    }

    @Test
    public void testSumOfInversePowersWithNegativeOverflow()
    {
        // values this large do not fit in the byte of an overflow entry, which becomes negative:
        // the sum must still match the sequential one, whatever that is for such a corrupt state
        for (int indexBitLength : new int[] {1, 4, 16}) {
            for (int value = 143; value <= 270; value++) {
                DenseHll hll = new DenseHll(indexBitLength);
                hll.insert(0, value);
                assertSumOfInversePowers(hll);
            }
        }
    }

    @Test
    public void testSumOfInversePowersWhenOrderOfAdditionsMatters()
    {
        // 2^16 - 4 buckets with value 0 followed by 4 buckets with value 38: adding 2^-38 to
        // 65532 one bucket at a time rounds back to 65532, while the exact sum is 65532 + 2^-36
        assertSumOfInversePowersWithTopBuckets(38, 65532.0);

        // with value 37 all partial sums are exact
        assertSumOfInversePowersWithTopBuckets(37, 65532.0 + 0x1p-35);
    }

    @Test
    public void testSumOfInversePowersAtGuardBoundaries()
    {
        // baseline + maxDelta around Long.SIZE - 1 (no overflows, so maxDelta is 15)
        for (int baseline = 45; baseline <= 50; baseline++) {
            assertSumOfInversePowers(deserialize(1, baseline, new byte[] {(byte) 0xFF}, new int[0], new int[0]));
            assertSumOfInversePowers(deserialize(4, baseline, new byte[] {(byte) 0xFF, 0x0F, (byte) 0xF0, 0x11, 0x22, 0x33, 0x44, 0x55}, new int[0], new int[0]));
        }

        // negative baselines are corrupt
        for (int baseline : new int[] {-1, Byte.MIN_VALUE}) {
            assertSumOfInversePowers(deserialize(4, baseline, new byte[8], new int[0], new int[0]));
        }

        // indexBitLength + maxDelta on both sides of 53
        for (int overflow = 33; overflow <= 36; overflow++) {
            assertSumOfInversePowers(deserialize(4, 0, new byte[] {(byte) 0xFF, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77}, new int[] {0}, new int[] {overflow}));
        }
    }

    private static void assertSumOfInversePowersWithTopBuckets(int value, double expected)
    {
        DenseHll hll = new DenseHll(16);
        for (int bucket = (1 << 16) - 4; bucket < 1 << 16; bucket++) {
            hll.insert(bucket, value);
        }
        assertThat(hll.sumOfInversePowers()).isEqualTo(expected);
        assertSumOfInversePowers(hll);
    }

    @Test
    public void testRepeatedBaselineAdjustments()
    {
        for (int indexBitLength = 1; indexBitLength <= 16; indexBitLength++) {
            for (int delta : new int[] {1, 4, 14}) {
                int buckets = numberOfBuckets(indexBitLength);
                byte[] deltas = new byte[buckets / 2];
                Arrays.fill(deltas, (byte) (delta * 0x11));
                DenseHll hll = deserialize(indexBitLength, 0, deltas, new int[0], new int[0]);
                assertMergeLeavesSameState(hll, new DenseHll(indexBitLength));
                hll.verify();

                // Exercise overflow removal before, at, and after the boundaries between words.
                int[] overflowBuckets = IntStream.of(0, 15, 16, buckets - 1).filter(bucket -> bucket < buckets).distinct().toArray();
                int[] overflowValues = new int[overflowBuckets.length];
                for (int i = 0; i < overflowBuckets.length; i++) {
                    int bucket = overflowBuckets[i];
                    deltas[bucket / 2] |= (byte) (bucket % 2 == 0 ? 0xF0 : 0x0F);
                    overflowValues[i] = i + 1;
                }
                hll = deserialize(indexBitLength, 0, deltas, overflowBuckets, overflowValues);
                assertMergeLeavesSameState(hll, new DenseHll(indexBitLength));
                hll.verify();
            }
        }
    }

    @Test
    public void testMergeLeavesSameStateAsBucketAtATimeMerge()
    {
        Random random = new Random(1);
        for (int iteration = 0; iteration < 3_000; iteration++) {
            int indexBitLength = 1 + random.nextInt(16);
            // equal baselines, baselines that are close, and baselines further apart than the largest delta
            int baseline = random.nextInt(40);
            int otherBaseline = switch (random.nextInt(4)) {
                case 0 -> baseline;
                case 1 -> baseline + 1 + random.nextInt(3);
                case 2 -> baseline + 1 + random.nextInt(14);
                default -> baseline + 15 + random.nextInt(10);
            };
            boolean corrupt = iteration % 5 == 0;

            DenseHll hll = randomHll(random, indexBitLength, baseline, corrupt);
            DenseHll other = randomHll(random, indexBitLength, otherBaseline, corrupt);
            if (random.nextBoolean()) {
                DenseHll swapped = hll;
                hll = other;
                other = swapped;
            }

            assertMergeLeavesSameState(hll, other);
            if (!corrupt) {
                hll.verify();
            }
        }
    }

    @Test
    public void testMergeOfInsertedHashesLeavesSameStateAsBucketAtATimeMerge()
    {
        Random random = new Random(2);
        for (int iteration = 0; iteration < 300; iteration++) {
            int indexBitLength = 1 + random.nextInt(16);
            DenseHll hll = new DenseHll(indexBitLength);
            DenseHll other = new DenseHll(indexBitLength);
            // up to about 250 values per bucket
            for (int i = random.nextInt(1 << (indexBitLength + random.nextInt(9))); i > 0; i--) {
                hll.insertHash(random.nextLong());
            }
            for (int i = random.nextInt(1 << (indexBitLength + random.nextInt(9))); i > 0; i--) {
                other.insertHash(random.nextLong());
            }

            assertMergeLeavesSameState(hll, other);
            hll.verify();

            // merging an HLL with itself
            assertMergeLeavesSameState(other, other);
            other.verify();
        }
    }

    private static DenseHll randomHll(Random random, int indexBitLength, int baseline, boolean corrupt)
    {
        int numberOfBuckets = numberOfBuckets(indexBitLength);

        // without any zero delta, the baseline has to be adjusted after the merge
        int zeroDeltasPercentage = new int[] {0, 0, 1, 20, 80}[random.nextInt(5)];
        byte[] deltas = new byte[numberOfBuckets / 2];
        for (int i = 0; i < deltas.length; i++) {
            int high = random.nextInt(100) < zeroDeltasPercentage ? 0 : 1 + random.nextInt(14);
            int low = random.nextInt(100) < zeroDeltasPercentage ? 0 : 1 + random.nextInt(14);
            deltas[i] = (byte) (high << 4 | low);
        }

        // some of the buckets with the maximum delta have an overflow
        int[] maxDeltaBuckets = random.ints(0, numberOfBuckets).distinct().limit(random.nextInt(Math.min(numberOfBuckets, 12) + 1)).toArray();
        for (int bucket : maxDeltaBuckets) {
            deltas[bucket / 2] |= (byte) (bucket % 2 == 0 ? 0xF0 : 0x0F);
        }
        int[] overflowBuckets = IntStream.of(maxDeltaBuckets).filter(_ -> random.nextBoolean()).toArray();
        if (corrupt) {
            // the entries may repeat a bucket, point to a bucket with a smaller delta, or to the bucket past the last one
            overflowBuckets = random.ints(random.nextInt(Math.min(numberOfBuckets, 12) + 1), 0, Math.min(numberOfBuckets + 1, 1 << 16)).toArray();
            if (random.nextBoolean()) {
                baseline = (byte) random.nextInt();
            }
        }
        int[] overflowValues = new int[overflowBuckets.length];
        for (int i = 0; i < overflowValues.length; i++) {
            overflowValues[i] = 1 + random.nextInt(corrupt ? 127 : 20);
        }
        return deserialize(indexBitLength, baseline, deltas, overflowBuckets, overflowValues);
    }

    private static DenseHll deserialize(int indexBitLength, int baseline, byte[] deltas, int[] overflowBuckets, int[] overflowValues)
    {
        DynamicSliceOutput output = new DynamicSliceOutput(deltas.length + 3 * overflowBuckets.length + 5)
                .appendByte(Format.DENSE_V2.getTag())
                .appendByte(indexBitLength)
                .appendByte(baseline)
                .appendBytes(deltas)
                .appendShort(overflowBuckets.length);
        for (int bucket : overflowBuckets) {
            output.appendShort(bucket);
        }
        for (int value : overflowValues) {
            output.appendByte(value);
        }
        return new DenseHll(output.slice());
    }

    private static void assertSumOfInversePowers(DenseHll hll)
    {
        // adds the terms one bucket at a time, in bucket order
        double expected = 0;
        for (int i = 0; i < numberOfBuckets(hll.getIndexBitLength()); i++) {
            int value = hll.getValue(i);
            expected += 1.0 / (1L << value);
        }

        assertThat(Double.doubleToRawLongBits(hll.sumOfInversePowers())).isEqualTo(Double.doubleToRawLongBits(expected));
    }

    @Test
    public void testDeserializedBaselineCount()
    {
        Random random = new Random(1);
        for (int indexBitLength = 1; indexBitLength <= 16; indexBitLength++) {
            int numberOfBuckets = numberOfBuckets(indexBitLength);
            for (int zeroDeltasPercentage : new int[] {0, 1, 10, 30, 50, 70, 90, 99, 100}) {
                for (int iteration = 0; iteration < 20; iteration++) {
                    byte[] deltas = new byte[numberOfBuckets / 2];
                    for (int i = 0; i < deltas.length; i++) {
                        int high = random.nextInt(100) < zeroDeltasPercentage ? 0 : 1 + random.nextInt(15);
                        int low = random.nextInt(100) < zeroDeltasPercentage ? 0 : 1 + random.nextInt(15);
                        deltas[i] = (byte) (high << 4 | low);
                    }

                    Slice serialized = new DynamicSliceOutput(deltas.length + 5)
                            .appendByte(Format.DENSE_V2.getTag())
                            .appendByte(indexBitLength)
                            .appendByte(0) // baseline
                            .appendBytes(deltas)
                            .appendShort(0) // overflows
                            .slice();
                    DenseHll hll = new DenseHll(serialized);

                    // compares the baseline count with the number of zero deltas found one bucket at a time
                    hll.verify();
                }
            }
        }
    }

    private static void verifyMerge(int prefixBitLength, List<Long> one, List<Long> two)
    {
        DenseHll hll1 = new DenseHll(prefixBitLength);
        DenseHll hll2 = new DenseHll(prefixBitLength);

        DenseHll expected = new DenseHll(prefixBitLength);

        for (long value : one) {
            long hash = XxHash64.hash(value);
            hll1.insertHash(hash);
            expected.insertHash(hash);
        }

        for (long value : two) {
            long hash = XxHash64.hash(value);
            hll2.insertHash(hash);
            expected.insertHash(hash);
        }

        hll1.verify();
        hll2.verify();

        hll1.mergeWith(hll2);
        hll1.verify();

        assertThat(hll1.cardinality()).isEqualTo(expected.cardinality());
        assertSlicesEqual(hll1.serialize(), expected.serialize());
    }

    private static void assertSameBuckets(TestingHll testingHll, DenseHll hll)
    {
        for (int i = 0; i < testingHll.getBuckets().length; i++) {
            assertThat(hll.getValue(i)).isEqualTo(testingHll.getBuckets()[i]);
        }
    }

    private int[] prefixLengths()
    {
        return new int[] {4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    }

    private static void assertMergeLeavesSameState(DenseHll hll, DenseHll other)
    {
        BucketAtATimeHll expected = new BucketAtATimeHll(hll);
        BucketAtATimeHll expectedOther = hll == other ? expected : new BucketAtATimeHll(other);

        expected.mergeWith(expectedOther);
        hll.mergeWith(other);

        expected.assertSameState(hll);
        expectedOther.assertSameState(other);
    }

    /**
     * Copy of the state of a DenseHll, with mergeWith(DenseHll) as it was when it merged one bucket at a time
     */
    private static final class BucketAtATimeHll
    {
        private static final int MAX_DELTA = 15;
        private static final int OVERFLOW_GROW_INCREMENT = 5;

        private byte baseline;
        private int baselineCount;
        private final byte[] deltas;

        private int overflows;
        private int[] overflowBuckets;
        private byte[] overflowValues;

        public BucketAtATimeHll(DenseHll hll)
        {
            baseline = (byte) getField(hll, "baseline");
            baselineCount = (int) getField(hll, "baselineCount");
            deltas = ((byte[]) getField(hll, "deltas")).clone();
            overflows = (int) getField(hll, "overflows");
            overflowBuckets = ((int[]) getField(hll, "overflowBuckets")).clone();
            overflowValues = ((byte[]) getField(hll, "overflowValues")).clone();
        }

        public void assertSameState(DenseHll hll)
        {
            assertThat(getField(hll, "baseline")).isEqualTo(baseline);
            assertThat(getField(hll, "baselineCount")).isEqualTo(baselineCount);
            assertThat((byte[]) getField(hll, "deltas")).isEqualTo(deltas);
            assertThat(getField(hll, "overflows")).isEqualTo(overflows);
            // the arrays have to match in full, including the unused entries
            assertThat((int[]) getField(hll, "overflowBuckets")).isEqualTo(overflowBuckets);
            assertThat((byte[]) getField(hll, "overflowValues")).isEqualTo(overflowValues);
        }

        private static Object getField(DenseHll hll, String name)
        {
            try {
                Field field = DenseHll.class.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(hll);
            }
            catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        public void mergeWith(BucketAtATimeHll other)
        {
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

            adjustBaselineIfNeeded();
        }

        private void adjustBaselineIfNeeded()
        {
            while (baselineCount == 0) {
                baseline++;

                for (int bucket = 0; bucket < deltas.length * 2; ++bucket) {
                    int delta = getDelta(bucket);

                    boolean hasOverflow = false;
                    if (delta == MAX_DELTA) {
                        for (int i = 0; i < overflows; i++) {
                            if (overflowBuckets[i] == bucket) {
                                hasOverflow = true;
                                overflowValues[i]--;

                                if (overflowValues[i] == 0) {
                                    int lastEntry = overflows - 1;
                                    if (i < lastEntry) {
                                        overflowBuckets[i] = overflowBuckets[lastEntry];
                                        overflowValues[i] = overflowValues[lastEntry];

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
                        delta--;
                        setDelta(bucket, delta);
                    }

                    if (delta == 0) {
                        ++baselineCount;
                    }
                }
            }
        }

        private int getDelta(int bucket)
        {
            return (deltas[bucket >> 1] >> shiftForBucket(bucket)) & 0b1111;
        }

        private void setDelta(int bucket, int value)
        {
            int slot = bucket >> 1;
            deltas[slot] = (byte) (deltas[slot] & ~(0b1111 << shiftForBucket(bucket)) | (value << shiftForBucket(bucket)));
        }

        private static int shiftForBucket(int bucket)
        {
            return ((~bucket) & 1) << 2;
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
            int entry = findOverflowEntry(bucket);
            return entry == -1 ? 0 : overflowValues[entry];
        }

        private int updateOverflow(int bucket, int overflowEntry, int delta)
        {
            if (delta > MAX_DELTA) {
                if (overflowEntry != -1) {
                    overflowValues[overflowEntry] = (byte) (delta - MAX_DELTA);
                }
                else {
                    overflowBuckets = Ints.ensureCapacity(overflowBuckets, overflows + 1, OVERFLOW_GROW_INCREMENT);
                    overflowValues = Bytes.ensureCapacity(overflowValues, overflows + 1, OVERFLOW_GROW_INCREMENT);

                    overflowBuckets[overflows] = bucket;
                    overflowValues[overflows] = (byte) (delta - MAX_DELTA);
                    overflows++;
                }
                delta = MAX_DELTA;
            }
            else if (overflowEntry != -1) {
                overflowBuckets[overflowEntry] = overflowBuckets[overflows - 1];
                overflowValues[overflowEntry] = overflowValues[overflows - 1];
                overflows--;
            }

            return delta;
        }
    }
}
