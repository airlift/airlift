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
import io.airlift.slice.Murmur3;
import io.airlift.slice.Slice;
import io.airlift.slice.SliceOutput;
import org.testng.annotations.Test;

import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;

public class TestDenseSerialization
{
    @Test
    public void testEmpty()
            throws Exception
    {
        SliceOutput expected = new DynamicSliceOutput(1)
                .appendByte(0b0_000_0000) // type + version
                .appendByte(12) // p
                .appendByte(0); // baseline

        for (int i = 0; i < 1 << (12 - 1); i++) {
            expected.appendByte(0);
        }

        // overflow bucket
        expected.appendByte(0xff)
                .appendByte(0xff);

        // overflow value
        expected.appendByte(0);

        assertSlicesEqual(makeHll(12).serialize(), expected.slice());
    }

    @Test
    public void testSingleNoOverflow()
            throws Exception
    {
        byte[] buckets = new byte[1 << (12 - 1)];
        buckets[326] = 0b0000_0001;

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(0b0_000_0000) // type + version
                .appendByte(12) // p
                .appendByte(0) // baseline
                .appendBytes(buckets) // buckets
                        // overflow bucket
                .appendByte(0xff)
                .appendByte(0xff)
                        // overflow value
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
                .appendByte(0b0_000_0000) // type + version
                .appendByte(12) // p
                .appendByte(0) // baseline
                .appendBytes(buckets) // buckets
                        // overflow bucket
                .appendByte(0x92)
                .appendByte(0xA)
                        // overflow value
                .appendByte(2)
                .slice();

        assertSlicesEqual(makeHll(12,61697).serialize(), expected);
    }

    @Test
    public void testMultipleOverflow()
            throws Exception
    {
        byte[] buckets = new byte[1 << (12 - 1)];
        buckets[1353] = (byte) 0b1111_0000;
        buckets[2024] = (byte) 0b1111_0000;

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(0b0_000_0000) // type + version
                .appendByte(12) // p
                .appendByte(0) // baseline
                .appendBytes(buckets) // buckets
                        // overflow bucket
                .appendByte(0xD0)
                .appendByte(0xF)
                        // overflow value
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
                .appendByte(0b0_000_0000) // type + version
                .appendByte(4) // p
                .appendByte(2) // baseline
                .appendBytes(buckets) // buckets
                        // overflow bucket
                .appendByte(0xff)
                .appendByte(0xff)
                        // overflow value
                .appendByte(0)
                .slice();

        DenseHll hll = new DenseHll(4);

        for (int i = 0; i < 100; i++) {
            hll.insertHash(Murmur3.hash64(i));
        }

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testOverflowAfterBaselineIncrement()
            throws Exception
    {
        byte[] buckets = new byte[] {0x45, 0x23, 0x01, 0x31, 0x22, 0x05, 0x04, (byte) 0xF1};

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(0b0_000_0000) // type + version
                .appendByte(4) // p
                .appendByte(2) // baseline
                .appendBytes(buckets) // buckets
                        // overflow bucket
                .appendByte(14)
                .appendByte(0)
                        // overflow value
                .appendByte(5)
                .slice();

        DenseHll hll = new DenseHll(4);

        for (int i = 0; i < 100; i++) {
            hll.insertHash(Murmur3.hash64(i));
        }
        hll.insertHash(Murmur3.hash64(37227));

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testBaselineAdjustmentAfterOverflow()
            throws Exception
    {
        byte[] buckets = new byte[] {0x45, 0x23, 0x01, 0x31, 0x22, 0x05, 0x04, (byte) 0xF1};

        Slice expected = new DynamicSliceOutput(1)
                .appendByte(0b0_000_0000) // type + version
                .appendByte(4) // p
                .appendByte(2) // baseline
                .appendBytes(buckets) // buckets
                        // overflow bucket
                .appendByte(14)
                .appendByte(0)
                        // overflow value
                .appendByte(5)
                .slice();

        DenseHll hll = new DenseHll(4);

        hll.insertHash(Murmur3.hash64(37227));
        for (int i = 0; i < 100; i++) {
            hll.insertHash(Murmur3.hash64(i));
        }

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testRoundtrip()
            throws Exception
    {
        DenseHll hll = new DenseHll(4);

        for (int i = 0; i < 1000; i++) {
            hll.insertHash(Murmur3.hash64(i));

            Slice serialized = hll.serialize();
            Slice reserialized = new DenseHll(serialized).serialize();

            assertSlicesEqual(serialized, reserialized);
        }
    }

    private static DenseHll makeHll(int indexBits, long... values)
    {
        DenseHll result = new DenseHll(indexBits);
        for (long value : values) {
            result.insertHash(Murmur3.hash64(value));
        }
        return result;
    }
}
