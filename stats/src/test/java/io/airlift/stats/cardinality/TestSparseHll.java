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

import com.google.common.collect.ImmutableList;
import io.airlift.slice.Murmur3Hash128;
import org.openjdk.jol.info.ClassLayout;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static io.airlift.slice.SizeOf.sizeOf;
import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;
import static io.airlift.stats.cardinality.TestUtils.sequence;
import static org.testng.Assert.assertEquals;

public class TestSparseHll
{
    private static final int SPARSE_HLL_INSTANCE_SIZE = ClassLayout.parseClass(SparseHll.class).instanceSize();;

    @Test(dataProvider = "bits")
    public void testMerge(int prefixBitLength)
            throws Exception
    {
        // with overlap
        verifyMerge(prefixBitLength, sequence(0, 100), sequence(50, 150));
        verifyMerge(prefixBitLength, sequence(50, 150), sequence(0, 100));

        // no overlap
        verifyMerge(prefixBitLength, sequence(0, 100), sequence(200, 300));
        verifyMerge(prefixBitLength, sequence(200, 300), sequence(0, 100));

        // idempotent
        verifyMerge(prefixBitLength, sequence(0, 100), sequence(0, 100));

        // multiple overflows (some with same index)
        verifyMerge(prefixBitLength, ImmutableList.of(29678L, 54004L), ImmutableList.of(64034L, 20591L, 56987L));
        verifyMerge(prefixBitLength, ImmutableList.of(64034L, 20591L, 56987L), ImmutableList.of(29678L, 54004L));
    }

    @Test(dataProvider = "bits")
    public void testToDense(int prefixBitLength)
            throws Exception
    {
        verifyToDense(prefixBitLength, sequence(0, 10000));

        // special cases with overflows
        verifyToDense(prefixBitLength, ImmutableList.of(201L, 280L));
        verifyToDense(prefixBitLength, ImmutableList.of(224L, 271L));
    }

    @Test
    public void testRetainedSize()
    {
        SparseHll sparseHll = new SparseHll(10);
        // use an empty array to mock the entries in SparseHll
        int[] entries = new int[1];
        long retainedSize = sizeOf(entries) + SPARSE_HLL_INSTANCE_SIZE;
        for (int value = 0; value < 100; value++) {
            sparseHll.insertHash(Murmur3Hash128.hash64(value));
            if (value % 10 == 1) {
                // we increase the capacity by 10 once full
                entries = new int[value + 10];
                retainedSize = sizeOf(entries) + SPARSE_HLL_INSTANCE_SIZE;
            }
            assertEquals(sparseHll.estimatedInMemorySize(), retainedSize);
        }
    }

    private static void verifyMerge(int prefixBitLength, List<Long> one, List<Long> two)
    {
        SparseHll hll1 = new SparseHll(prefixBitLength);
        SparseHll hll2 = new SparseHll(prefixBitLength);

        SparseHll expected = new SparseHll(prefixBitLength);

        for (long value : one) {
            long hash = Murmur3Hash128.hash64(value);
            hll1.insertHash(hash);
            expected.insertHash(hash);
        }

        for (long value : two) {
            long hash = Murmur3Hash128.hash64(value);
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

    private static void verifyToDense(int prefixBitLength, List<Long> values)
    {
        DenseHll expected = new DenseHll(prefixBitLength);
        SparseHll sparse = new SparseHll(prefixBitLength);

        for (long value : values) {
            long hash = Murmur3Hash128.hash64(value);
            sparse.insertHash(hash);
            expected.insertHash(hash);
        }

        sparse.verify();
        expected.verify();

        assertSlicesEqual(sparse.toDense().serialize(), expected.serialize());
    }

    @DataProvider(name = "bits")
    private Object[][] prefixLengths()
    {
        return new Object[][] {
                new Object[] { 4 },
                new Object[] { 5 },
                new Object[] { 6 },
                new Object[] { 7 },
                new Object[] { 8 },
                new Object[] { 9 },
                new Object[] { 10 },
                new Object[] { 11 },
                new Object[] { 12 },
                new Object[] { 13 },
                new Object[] { 14 },
                new Object[] { 15 },
        };
    }
}
