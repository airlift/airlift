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

import io.airlift.slice.Murmur3;
import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import org.testng.annotations.Test;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;
import static io.airlift.stats.cardinality.TestUtils.sequence;
import static org.testng.Assert.assertEquals;

public class TestDenseHll
{
    @Test
    public void testInsert()
            throws Exception
    {
        DenseHll hll = new DenseHll(11);
        for (int i = 0; i < 20000; i++) {
            hll.insertHash(Murmur3.hash64(i));
            hll.verify();
        }
    }

    @Test
    public void testMerge()
            throws Exception
    {
        // small, non-overlapping
        verifyMerge(sequence(0, 100), sequence(100, 200));
        verifyMerge(sequence(100, 200), sequence(0, 100));

        // small, overlapping
        verifyMerge(sequence(0, 100), sequence(50, 150));
        verifyMerge(sequence(50, 150), sequence(0, 100));

        // small, same
        verifyMerge(sequence(0, 100), sequence(0, 100));


        // large, non-overlapping
        verifyMerge(sequence(0, 20000), sequence(20000, 40000));
        verifyMerge(sequence(20000, 40000), sequence(0, 20000));

        // large, overlapping
        verifyMerge(sequence(0, 20000), sequence(10000, 30000));
        verifyMerge(sequence(10000, 30000), sequence(0, 20000));

        // large, same
        verifyMerge(sequence(0, 20000), sequence(0, 20000));
    }

    private static void verifyMerge(List<Long> one, List<Long> two)
    {
        DenseHll hll1 = new DenseHll(11);
        DenseHll hll2 = new DenseHll(11);

        DenseHll expected = new DenseHll(11);

        for (long value : one) {
            long hash = Murmur3.hash64(value);
            hll1.insertHash(hash);
            expected.insertHash(hash);
        }

        for (long value : two) {
            long hash = Murmur3.hash64(value);
            hll2.insertHash(hash);
            expected.insertHash(hash);
        }

        hll1.verify();
        hll2.verify();

        hll1.mergeWith(hll2);
        hll1.verify();

        assertEquals(hll1.cardinality(), expected.cardinality());
        assertSlicesEqual(hll1.serialize(), expected.serialize());
    }
}
