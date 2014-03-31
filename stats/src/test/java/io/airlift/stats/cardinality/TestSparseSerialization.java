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
import io.airlift.slice.Slices;
import org.testng.annotations.Test;

import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;

public class TestSparseSerialization
{
    @Test
    public void testEmpty()
            throws Exception
    {
        Slice expected = new DynamicSliceOutput(1)
                .appendByte(0b1_000_0000) // type + version
                .appendByte(12) // p
                        // number of short hashes
                .appendByte(0)
                .appendByte(0)
                        // number of overflows
                .appendByte(0)
                .appendByte(0)
                .slice();

        SparseHll hll = new SparseHll(12);

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testSingleNoOverflow()
            throws Exception
    {
        Slice expected = new DynamicSliceOutput(1)
                .appendByte(0b1_000_0000) // type + version
                .appendByte(12) // p
                        // number of short hashes
                .appendByte(1)
                .appendByte(0)
                        // number of overflows
                .appendByte(0)
                .appendByte(0)
                        // hash 0
                .appendByte(0b0001_0000)
                .appendByte(0b0100_0110)
                .slice();

        SparseHll hll = new SparseHll(12);

        hll.insertHash(Murmur3.hash64(Slices.wrappedBuffer(new byte[] {0})));

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testSingleWithOverflow()
            throws Exception
    {
        Slice expected = new DynamicSliceOutput(1)
                .appendByte(0b1000_0000) // type + version
                .appendByte(12) // p
                        // number of short hashes
                .appendByte(1)
                .appendByte(0)
                        // number of overflows
                .appendByte(1)
                .appendByte(0)
                        // short hash
                .appendByte(0b0010_0000)
                .appendByte(0b0010_0001)
                        // overflow entry
                .appendByte(0b0010_0001)
                .appendByte(0b0010_0001)
                .slice();

        SparseHll hll = new SparseHll(12);

        hll.insertHash(Murmur3.hash64(Slices.wrappedBuffer(new byte[] {64})));

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testMultipleWithOverflow()
            throws Exception
    {
        Slice expected = new DynamicSliceOutput(1)
                .appendByte(0b1000_0000) // type + version
                .appendByte(12) // p
                        // number of short hashes
                .appendByte(2)
                .appendByte(0)
                        // number of overflows
                .appendByte(2)
                .appendByte(0)
                        // short hash 0
                .appendByte(0b0010_0000)
                .appendByte(0b0010_0001)
                        // short hash 1
                .appendByte(0b1000_0000)
                .appendByte(0b1010_0100)
                        // overflow 0
                .appendByte(0b0010_0001)
                .appendByte(0b0010_0001)
                        // overflow 1
                .appendByte(0b1000_0001)
                .appendByte(0b1010_0100)
                .slice();

        SparseHll hll = new SparseHll(12);

        hll.insertHash(Murmur3.hash64(Slices.wrappedBuffer(new byte[] {64})));
        hll.insertHash(Murmur3.hash64(Slices.wrappedBuffer(new byte[] {(byte) 205})));

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testRoundtrip()
            throws Exception
    {
        SparseHll hll = new SparseHll(4);

        for (int i = 0; i < 1000; i++) {
            hll.insertHash(Murmur3.hash64(i));

            Slice serialized = hll.serialize();

            SparseHll deserialized = new SparseHll(serialized);
            Slice reserialized = deserialized.serialize();

            assertSlicesEqual(serialized, reserialized);
        }
    }

}
