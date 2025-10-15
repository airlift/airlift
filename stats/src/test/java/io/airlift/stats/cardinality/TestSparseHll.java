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

import static io.airlift.slice.SizeOf.instanceSize;
import static io.airlift.slice.SizeOf.sizeOf;
import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;
import static io.airlift.stats.cardinality.TestUtils.sequence;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import io.airlift.slice.Murmur3Hash128;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TestSparseHll {
    private static final int SPARSE_HLL_INSTANCE_SIZE = instanceSize(SparseHll.class);

    @Test
    public void testNumberOfZeros() {
        for (int indexBitLength : prefixLengths()) {
            for (int i = 0; i < 64 - indexBitLength; i++) {
                long hash = 1L << i;
                int expectedValue = Long.numberOfLeadingZeros(hash << indexBitLength) + 1;

                SparseHll sparseHll = new SparseHll(indexBitLength);
                sparseHll.insertHash(hash);
                sparseHll.eachBucket((bucket, value) -> {
                    assertThat(bucket).isEqualTo(0);
                    assertThat(value).isEqualTo(expectedValue);
                });
            }
        }
    }

    @Test
    public void testMerge() {
        for (int prefixBitLength : prefixLengths()) {
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
    }

    @Test
    public void testToDense() {
        for (int prefixBitLength : prefixLengths()) {
            verifyToDense(prefixBitLength, sequence(0, 10000));

            // special cases with overflows
            verifyToDense(prefixBitLength, ImmutableList.of(201L, 280L));
            verifyToDense(prefixBitLength, ImmutableList.of(224L, 271L));
        }
    }

    @Test
    public void testRetainedSize() {
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
            assertThat(sparseHll.estimatedInMemorySize()).isEqualTo(retainedSize);
        }
    }

    private static void verifyMerge(int prefixBitLength, List<Long> one, List<Long> two) {
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

        assertThat(hll1.cardinality()).isEqualTo(expected.cardinality());
        assertSlicesEqual(hll1.serialize(), expected.serialize());
    }

    private static void verifyToDense(int prefixBitLength, List<Long> values) {
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

    private int[] prefixLengths() {
        return new int[] {4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    }
}
