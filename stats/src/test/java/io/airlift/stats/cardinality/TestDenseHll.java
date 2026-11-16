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

import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.Slice;
import io.airlift.slice.XxHash64;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

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
}
