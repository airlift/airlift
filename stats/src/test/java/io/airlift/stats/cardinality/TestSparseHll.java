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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import io.airlift.slice.Murmur3;
import org.testng.annotations.Test;
import org.testng.collections.SetMultiMap;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;
import static io.airlift.stats.cardinality.TestUtils.sequence;
import static org.testng.Assert.assertEquals;

public class TestSparseHll
{
    @Test
    public void testMerge()
            throws Exception
    {
        // with overlap
        verifyMerge(sequence(0, 100), sequence(50, 150));
        verifyMerge(sequence(50, 150), sequence(0, 100));

        // no overlap
        verifyMerge(sequence(0, 100), sequence(200, 300));
        verifyMerge(sequence(200, 300), sequence(0, 100));

        // idempotent
        verifyMerge(sequence(0, 100), sequence(0, 100));

        // multiple overflows (some with same index)
        verifyMerge(ImmutableList.of(29678L, 54004L), ImmutableList.of(64034L, 20591L, 56987L));
        verifyMerge(ImmutableList.of(64034L, 20591L, 56987L), ImmutableList.of(29678L, 54004L));
    }

    @Test
    public void testToDense()
            throws Exception
    {
        verifyToDense(sequence(0, 10000));

        // special cases with overflows
        verifyToDense(ImmutableList.of(201L, 280L));
        verifyToDense(ImmutableList.of(224L, 271L));
    }

//    @Test
    public void testSomething()
            throws Exception
    {
        Multimap<Integer, Integer> values = ArrayListMultimap.create();

        for (int i = 0; i < 100000; i++) {
            long hash = Murmur3.hash64(i);
            int index = Utils.computeIndex(hash, 11);
            int zeros = Long.numberOfLeadingZeros(hash << 11);
            if (zeros > 5) {
                values.put(index, i);
//                System.out.println(i + ","+ zeros);
            }
        }

        System.out.println(values);
    }

    private static void verifyMerge(List<Long> one, List<Long> two)
    {
        SparseHll hll1 = new SparseHll(11);
        SparseHll hll2 = new SparseHll(11);

        SparseHll expected = new SparseHll(11);

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

    private static void verifyToDense(List<Long> values)
    {
        DenseHll expected = new DenseHll(11);
        SparseHll sparse = new SparseHll(11);

        for (long value : values) {
            long hash = Murmur3.hash64(value);
            sparse.insertHash(hash);
            expected.insertHash(hash);
        }

        sparse.verify();
        expected.verify();

        assertSlicesEqual(sparse.toDense().serialize(), expected.serialize());
    }
}
