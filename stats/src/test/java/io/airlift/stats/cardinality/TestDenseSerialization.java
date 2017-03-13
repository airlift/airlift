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
import io.airlift.slice.Murmur3Hash128;
import io.airlift.slice.Slice;
import io.airlift.slice.SliceOutput;
import org.testng.annotations.Test;

import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;
import static io.airlift.stats.cardinality.Utils.numberOfBuckets;
import static org.testng.Assert.assertEquals;

public class TestDenseSerialization
{
    @Test
    public void testEmpty()
            throws Exception
    {
        SliceOutput expected = new DynamicSliceOutput(1)
                .appendByte(3)  // format tag
                .appendByte(12) // p
                .appendByte(0); // baseline

        for (int i = 0; i < 1 << (12 - 1); i++) {
            expected.appendByte(0);
        }

        // overflows
        expected.appendByte(0)
                .appendByte(0);

        assertSlicesEqual(makeHll(12).serialize(), expected.slice());
    }

    @Test
    public void testSingleNoOverflow()
            throws Exception
    {
        byte[] buckets = new byte[1 << (12 - 1)];
        buckets[326] = 0b0000_0001;

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(3)  // format tag
                .appendByte(12) // p
                .appendByte(0)  // baseline
                .appendBytes(buckets) // buckets
                        // overflows
                .appendByte(0)
                .appendByte(0)
                .slice();

        assertSlicesEqual(makeHll(12, 0).serialize(), expected);
    }

    @Test
    public void testSingleWithOverflow()
            throws Exception
    {
        byte[] buckets = new byte[1 << (12 - 1)];
        buckets[1353] = (byte) 0b1111_0000;

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(3)  // format tag
                .appendByte(12) // p
                .appendByte(0)  // baseline
                .appendBytes(buckets) // buckets
                        // overflows
                .appendByte(1)
                .appendByte(0)
                        // overflow bucket
                .appendByte(0x92)
                .appendByte(0xA)
                        // overflow value
                .appendByte(2)
                .slice();

        assertSlicesEqual(makeHll(12, 61697).serialize(), expected);
    }

    @Test
    public void testMultipleOverflow()
            throws Exception
    {
        byte[] buckets = new byte[1 << (12 - 1)];
        buckets[1353] = (byte) 0b1111_0000;
        buckets[2024] = (byte) 0b1111_0000;

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(3)  // format tag
                .appendByte(12) // p
                .appendByte(0)  // baseline
                .appendBytes(buckets) // buckets
                        // overflows
                .appendByte(2)
                .appendByte(0)
                        // overflow bucket
                .appendByte(146)
                .appendByte(10)
                .appendByte(208)
                .appendByte(15)
                        // overflow value
                .appendByte(2)
                .appendByte(4)
                .slice();

        assertSlicesEqual(makeHll(12, 61697, 394873).serialize(), expected);

        // test commutativity
        assertSlicesEqual(makeHll(12, 394873, 61697).serialize(), expected);
    }

    @Test
    public void testMergeWithOverflows()
            throws Exception
    {
        DenseHll expected = makeHll(4, 37227, 93351);

        assertSlicesEqual(
                makeHll(4, 37227).mergeWith(makeHll(4, 93351)).serialize(),
                expected.serialize());

        // test commutativity
        assertSlicesEqual(
                makeHll(4, 93351).mergeWith(makeHll(4, 37227)).serialize(),
                expected.serialize());
    }

    @Test
    public void testBaselineAdjusment()
            throws Exception
    {
        byte[] buckets = new byte[] {0x45, 0x23, 0x01, 0x31, 0x22, 0x05, 0x04, 0x01};

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(3) // format tag
                .appendByte(4) // p
                .appendByte(2) // baseline
                .appendBytes(buckets) // buckets
                        // overflows
                .appendByte(0)
                .appendByte(0)
                .slice();

        DenseHll hll = new DenseHll(4);

        for (int i = 0; i < 100; i++) {
            hll.insertHash(Murmur3Hash128.hash64(i));
        }

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testOverflowAfterBaselineIncrement()
            throws Exception
    {
        byte[] buckets = new byte[] {0x45, 0x23, 0x01, 0x31, 0x22, 0x05, 0x04, (byte) 0xF1};

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(3) // format tag
                .appendByte(4) // p
                .appendByte(2) // baseline
                .appendBytes(buckets) // buckets
                        // overflows
                .appendByte(1)
                .appendByte(0)
                        // overflow bucket
                .appendByte(14)
                .appendByte(0)
                        // overflow value
                .appendByte(5)
                .slice();

        DenseHll hll = new DenseHll(4);

        for (int i = 0; i < 100; i++) {
            hll.insertHash(Murmur3Hash128.hash64(i));
        }
        hll.insertHash(Murmur3Hash128.hash64(37227));

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testBaselineAdjustmentAfterOverflow()
            throws Exception
    {
        byte[] buckets = new byte[] {0x45, 0x23, 0x01, 0x31, 0x22, 0x05, 0x04, (byte) 0xF1};

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(3) // format tag
                .appendByte(4) // p
                .appendByte(2) // baseline
                .appendBytes(buckets) // buckets
                        // overflows
                .appendByte(1)
                .appendByte(0)
                        // overflow bucket
                .appendByte(14)
                .appendByte(0)
                        // overflow value
                .appendByte(5)
                .slice();

        DenseHll hll = new DenseHll(4);

        hll.insertHash(Murmur3Hash128.hash64(37227));
        for (int i = 0; i < 100; i++) {
            hll.insertHash(Murmur3Hash128.hash64(i));
        }

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testRoundtrip()
            throws Exception
    {
        DenseHll hll = new DenseHll(4);

        for (int i = 0; i < 1000; i++) {
            hll.insertHash(Murmur3Hash128.hash64(i));

            Slice serialized = hll.serialize();
            Slice reserialized = new DenseHll(serialized).serialize();

            assertSlicesEqual(serialized, reserialized);
        }
    }

    @Test
    public void testDeserializeDenseV1NoOverflows()
            throws Exception
    {
        int indexBitLength = 4;
        int numberOfBuckets = numberOfBuckets(indexBitLength);
        Slice serialized = new DynamicSliceOutput(1)
                .appendByte(Format.DENSE_V1.getTag()) // format tag
                .appendByte(indexBitLength) // p
                .appendByte(10) // baseline
                .appendBytes(new byte[numberOfBuckets / 2]) // buckets
                        // overflow bucket
                .appendByte(0xFF)
                .appendByte(0xFF)
                        // overflow value
                .appendByte(0)
                .slice();

        DenseHll deserialized = new DenseHll(serialized);
        for (int i = 0; i < numberOfBuckets; i++) {
            assertEquals(deserialized.getValue(i), 10);
        }
        deserialized.verify();
    }

    @Test
    public void testDeserializeDenseV1EmptyOverflow()
            throws Exception
    {
        // bucket 1 has a value of 17 (i.e., baseline = 2, delta == 15 and overflow is present with a value of 0)

        int indexBitLength = 4;
        int numberOfBuckets = numberOfBuckets(indexBitLength);
        Slice serialized = new DynamicSliceOutput(1)
                .appendByte(Format.DENSE_V1.getTag()) // format tag
                .appendByte(indexBitLength) // p
                .appendByte(2) // baseline
                .appendBytes(new byte[] { 0x0F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}) // buckets
                        // overflow bucket
                .appendByte(0x01)
                .appendByte(0x00)
                        // overflow value
                .appendByte(0)
                .slice();

        DenseHll deserialized = new DenseHll(serialized);
        for (int i = 0; i < numberOfBuckets; i++) {
            if (i == 1) {
                assertEquals(deserialized.getValue(i), 17);
            }
            else {
                assertEquals(deserialized.getValue(i), 2);
            }
        }
        deserialized.verify();
    }

    @Test
    public void testDeserializeDenseV1Overflow()
            throws Exception
    {
        // bucket 1 has a value of 20 (i.e., baseline = 2, delta == 15, overflow == 3)

        int indexBitLength = 4;
        int numberOfBuckets = numberOfBuckets(indexBitLength);
        Slice serialized = new DynamicSliceOutput(1)
                .appendByte(Format.DENSE_V1.getTag()) // format tag
                .appendByte(indexBitLength) // p
                .appendByte(2) // baseline
                .appendBytes(new byte[] { 0x0F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}) // buckets
                        // overflow bucket
                .appendByte(0x01)
                .appendByte(0x00)
                        // overflow value
                .appendByte(3)
                .slice();

        DenseHll deserialized = new DenseHll(serialized);
        for (int i = 0; i < numberOfBuckets; i++) {
            if (i == 1) {
                assertEquals(deserialized.getValue(i), 20);
            }
            else {
                assertEquals(deserialized.getValue(i), 2);
            }
        }
        deserialized.verify();
    }

    private static DenseHll makeHll(int indexBits, long... values)
    {
        DenseHll result = new DenseHll(indexBits);
        for (long value : values) {
            result.insertHash(Murmur3Hash128.hash64(value));
        }
        return result;
    }
}
