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

import io.airlift.slice.XxHash64;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;
import static io.airlift.stats.cardinality.TestUtils.sequence;
import static io.airlift.stats.cardinality.Utils.numberOfBuckets;
import static org.testng.Assert.assertEquals;

public class TestDenseHll
{
    @Test(dataProvider = "bits")
    public void testMultipleMerges(int prefixBitLength)
            throws Exception
    {
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
            assertEquals(single.getValue(i), merged.getValue(i));
        }

        assertEquals(single.cardinality(), merged.cardinality());
    }

    @Test(dataProvider = "bits")
    public void testHighCardinality(int prefixBitLength)
            throws Exception
    {
        TestingHll testingHll = new TestingHll(prefixBitLength);
        DenseHll hll = new DenseHll(prefixBitLength);
        for (int i = 0; i < 10_000_000; i++) {
            long hash = XxHash64.hash(i);

            testingHll.insertHash(hash);
            hll.insertHash(hash);
        }

        assertSameBuckets(testingHll, hll);
    }

    @Test(dataProvider = "bits")
    public void testInsert(int prefixBitLength)
            throws Exception
    {
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

    @Test
    public void testMergeWithOverflows()
            throws Exception
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

    @Test(dataProvider = "bits")
    public void testMerge(int prefixBitLength)
            throws Exception
    {
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

        assertEquals(hll1.cardinality(), expected.cardinality());
        assertSlicesEqual(hll1.serialize(), expected.serialize());
    }

    private static void assertSameBuckets(TestingHll testingHll, DenseHll hll)
    {
        for (int i = 0; i < testingHll.getBuckets().length; i++) {
            assertEquals(hll.getValue(i), testingHll.getBuckets()[i]);
        }
    }

    @DataProvider(name = "bits")
    private Object[][] prefixLengths()
    {
        return new Object[][] {
                new Object[] {4},
                new Object[] {5},
                new Object[] {6},
                new Object[] {7},
                new Object[] {8},
                new Object[] {9},
                new Object[] {10},
                new Object[] {11},
                new Object[] {12},
                new Object[] {13},
                new Object[] {14},
                new Object[] {15},
                new Object[] {16},
        };
    }
}
