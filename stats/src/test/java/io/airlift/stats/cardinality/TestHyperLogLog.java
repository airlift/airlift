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

import io.airlift.slice.Slice;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static io.airlift.slice.testing.SliceAssertions.assertSlicesEqual;
import static io.airlift.stats.cardinality.TestUtils.sequence;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestHyperLogLog
{
    @Test
    public void testEstimates()
            throws Exception
    {
        int trials = 1000;
        for (int indexBits = 4; indexBits <= 13; indexBits++) {
            Map<Integer, Stats> errors = new HashMap<>();
            int numberOfBuckets = 1 << indexBits;
            int maxCardinality = numberOfBuckets * 2;

            for (int trial = 0; trial < trials; trial++) {
                HyperLogLog hll = HyperLogLog.newInstance(numberOfBuckets);
                for (int cardinality = 1; cardinality <= maxCardinality; cardinality++) {
                    hll.add(ThreadLocalRandom.current().nextLong());

                    if (cardinality % (numberOfBuckets / 10) == 0) {
                        // only do this a few times, since computing the cardinality is currently not
                        // as cheap as it should be
                        double error = (hll.cardinality() - cardinality) * 1.0 / cardinality;

                        Stats stats = errors.get(cardinality);
                        if (stats == null) {
                            stats = new Stats();
                            errors.put(cardinality, stats);
                        }

                        stats.add(error);
                    }
                }
            }

            double expectedStandardError = 1.04 / Math.sqrt(1 << indexBits);

            for (Map.Entry<Integer, Stats> entry : errors.entrySet()) {
                // Give an extra error margin. This is mostly a sanity check to catch egregious errors
                assertTrue(entry.getValue().stdev() <= expectedStandardError * 1.1,
                        String.format("Failed at p = %s, cardinality = %s. Expected std error = %s, actual = %s",
                                indexBits,
                                entry.getKey(),
                                expectedStandardError,
                                entry.getValue().stdev()));
            }
        }
    }

    @Test
    public void testMerge()
            throws Exception
    {
        // small vs small
        verifyMerge(sequence(0, 100), sequence(50, 150));

        // small vs big
        verifyMerge(sequence(0, 100), sequence(50, 5000));

        // big vs small
        verifyMerge(sequence(50, 5000), sequence(0, 100));

        // big vs big
        verifyMerge(sequence(0, 5000), sequence(3000, 8000));
    }

    private void verifyMerge(List<Long> one, List<Long> two)
    {
        HyperLogLog hll1 = HyperLogLog.newInstance(2048);
        HyperLogLog hll2 = HyperLogLog.newInstance(2048);

        HyperLogLog expected = HyperLogLog.newInstance(2048);

        for (long value : one) {
            hll1.add(value);
            expected.add(value);
        }

        for (long value : two) {
            hll2.add(value);
            expected.add(value);
        }

        hll1.verify();
        hll2.verify();

        hll1.mergeWith(hll2);
        hll1.verify();

        assertEquals(hll1.cardinality(), expected.cardinality());
        assertEquals(hll1.serialize(), expected.serialize());
    }

    @Test
    public void testRoundtrip()
            throws Exception
    {
        // small
        verifyRoundtrip(sequence(0, 100));

        // large
        verifyRoundtrip(sequence(0, 20000));
    }

    private void verifyRoundtrip(List<Long> sequence)
    {
        HyperLogLog hll = HyperLogLog.newInstance(2048);

        for (Long value : sequence) {
            hll.add(value);
        }

        hll.verify();

        Slice serialized = hll.serialize();
        HyperLogLog deserialized = HyperLogLog.newInstance(serialized);
        deserialized.verify();

        assertEquals(hll.cardinality(), deserialized.cardinality());

        Slice reserialized = deserialized.serialize();
        assertSlicesEqual(serialized, reserialized);
    }
}
