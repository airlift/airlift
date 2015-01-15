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
                .appendByte(2)  // format tag
                .appendByte(12) // p
                        // number of entries
                .appendByte(0)
                .appendByte(0)
                .slice();

        SparseHll hll = new SparseHll(12);

        assertSlicesEqual(hll.serialize(), expected);
    }

    @Test
    public void testSingle()
            throws Exception
    {
        Slice expected = new DynamicSliceOutput(1)
                .appendByte(2)  // format tag
                .appendByte(12) // p
                        // number of entries
                .appendByte(1)
                .appendByte(0)
                        // entry 0
                .appendByte(0b0100_0010)
                .appendByte(0b0011_0100)
                .appendByte(0b0010_0000)
                .appendByte(0b0010_0001)
                .slice();

        SparseHll hll = new SparseHll(12);

        hll.insertHash(Murmur3.hash64(Slices.wrappedBuffer(new byte[] {64})));

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
