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
package io.airlift.stats;

import com.google.common.collect.Lists;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class TestTDigest
{
    @Test
    public void testEmpty()
    {
        TDigest digest = new TDigest();
        assertTrue(Double.isNaN(digest.valueAt(0.5)));
        assertTrue(Double.isNaN(digest.getMin()));
        assertTrue(Double.isNaN(digest.getMax()));
    }

    @Test
    public void testMonotonicity()
    {
        TDigest digest = new TDigest();
        for (int i = 0; i < 100000; i++) {
            digest.add(ThreadLocalRandom.current().nextDouble());
        }

        double previous = -1;
        for (double quantile = 0; quantile <= 1; quantile += 1e-5) {
            double value = digest.valueAt(quantile);
            assertTrue(value >= previous);
            previous = value;
        }
    }

    @Test
    public void testBigJump()
    {
        TDigest digest = new TDigest(100);
        for (int i = 1; i < 20; i++) {
            digest.add(i);
        }
        digest.add(1_000_000);

        assertEquals(digest.valueAt(0.89999999), 18.0);
        assertEquals(digest.valueAt(0.9), 19.0);
        assertEquals(digest.valueAt(0.949999999), 19.0);
        assertEquals(digest.valueAt(0.95), 1_000_000.0);
    }

    @Test
    public void testBigJumpWithMerge()
    {
        TDigest digest = new TDigest(100);
        for (int i = 1; i < 1000; i++) {
            digest.add(i);
        }
        digest.add(1_000_000);

        assertEquals(digest.valueAt(0.998), 999.0);
        assertEquals(digest.valueAt(0.999), 1_000_000.0);
    }

    @Test
    public void testSmallCountQuantile()
    {
        TDigest digest = new TDigest(200);
        addAll(digest, Lists.newArrayList(15, 20, 32, 60));

        assertEquals(digest.valueAt(0.4), 20, 1e-10);
        assertEquals(digest.valueAt(0.25), 20, 1e-10);
        assertEquals(digest.valueAt(0.25 - 1e-10), 15, 1e-10);
        assertEquals(digest.valueAt(0.5 - 1e-10), 20, 1e-10);
        assertEquals(digest.valueAt(0.5), 32, 1e-10);
    }

    @Test
    public void testSingletonQuantiles()
    {
        double[] values = new double[20];
        TDigest digest = new TDigest(100);
        for (int i = 0; i < 20; i++) {
            digest.add(i);
            values[i] = i;
        }

        for (double quantile = 0; quantile <= 1; quantile += 1e-3) {
            double expected = values[(int) Math.floor(quantile * values.length)];
            assertEquals(digest.valueAt(quantile), expected);
        }
    }

    @Test
    public void testSingleValue()
    {
        TDigest digest = new TDigest();
        double value = ThreadLocalRandom.current().nextDouble() * 1000;
        digest.add(value);

        assertEquals(digest.valueAt(0), value, 0.001f);
        assertEquals(digest.valueAt(0.5), value, 0.001f);
        assertEquals(digest.valueAt(1), value, 0.001f);
    }

    @Test
    public void testWeight()
    {
        TDigest digest = new TDigest();
        digest.add(1, 80);
        digest.add(2, 20);

        assertEquals(digest.valueAt(0), 1.0);
        assertEquals(digest.valueAt(0.3), 1.0);
        assertEquals(digest.valueAt(0.9), 2.0);
        assertEquals(digest.valueAt(1), 2.0);
    }

    @Test
    public void testSerializationEmpty()
    {
        TDigest digest = new TDigest();
        TDigest deserialized = TDigest.deserialize(digest.serialize());

        assertSimilar(deserialized, digest);

        // ensure the internal arrays are initialized properly
        deserialized.add(10);
        assertEquals(deserialized.getCount(), 1.0);
        assertEquals(deserialized.valueAt(0.5), 10.0);
    }

    @Test
    public void testSerializationSingle()
    {
        TDigest digest = new TDigest();
        digest.add(1);

        TDigest deserialized = TDigest.deserialize(digest.serialize());

        assertSimilar(deserialized, digest);
        assertEquals(deserialized.valueAt(0), digest.valueAt(0));
        assertEquals(deserialized.valueAt(1), digest.valueAt(1));
    }

    @Test
    public void testSerializationComplex()
    {
        TDigest digest = new TDigest();
        addAll(digest, asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7));

        TDigest deserialized = TDigest.deserialize(digest.serialize());

        assertSimilar(deserialized, digest);

        for (double quantile = 0; quantile <= 1; quantile += 0.1) {
            assertEquals(deserialized.valueAt(quantile), digest.valueAt(quantile));
        }
    }

    @Test(invocationCount = 1000)
    public void testSerializationRandom()
    {
        TDigest digest = new TDigest();

        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            values.add(ThreadLocalRandom.current().nextInt());
        }

        addAll(digest, values);

        TDigest deserialized = TDigest.deserialize(digest.serialize());

        assertSimilar(deserialized, digest);

        for (double quantile = 0; quantile <= 1; quantile += 0.1) {
            assertEquals(deserialized.valueAt(quantile), digest.valueAt(quantile));
        }
    }

    @Test
    public void testAddNaN()
    {
        TDigest digest = new TDigest();

        assertThrows(IllegalArgumentException.class, () -> digest.add(1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> digest.add(Double.NaN, 1));
    }

    @Test
    public void testCopy()
    {
        TDigest digest = new TDigest();
        digest.add(1);
        digest.add(2);
        digest.add(3, 10);

        TDigest copy = TDigest.copyOf(digest);
        assertSimilar(copy, digest);

        for (double quantile = 0; quantile <= 1; quantile += 0.1) {
            assertEquals(copy.valueAt(quantile), digest.valueAt(quantile));
        }
    }

    @Test
    public void testCopyEmpty()
    {
        TDigest digest = new TDigest();
        TDigest copy = TDigest.copyOf(digest);
        assertSimilar(copy, digest);

        // ensure the internal arrays are initialized properly
        copy.add(10);
        assertEquals(copy.getCount(), 1.0);
        assertEquals(copy.valueAt(0.5), 10.0);
    }

    @Test
    public void testMerge()
    {
        TDigest first = new TDigest();
        addAll(first, Arrays.asList(1, 2, 3, 4, 5));

        TDigest second = new TDigest();
        addAll(second, Arrays.asList(4, 5, 6, 7, 8));

        TDigest merged = TDigest.copyOf(first);
        merged.mergeWith(second);

        assertEquals(merged.getMin(), 1.0);
        assertEquals(merged.getMax(), 8.0);
        assertEquals(merged.getCount(), 10.0);

        assertEquals(merged.valueAt(0), 1.0);
        assertEquals(merged.valueAt(0.5), 5.0);
        assertEquals(merged.valueAt(1), 8.0);
    }

    private void addAll(TDigest digest, List<Integer> values)
    {
        for (int value : values) {
            digest.add(value);
        }
    }

    private void assertSimilar(TDigest actual, TDigest expected)
    {
        assertEquals(actual.getMin(), expected.getMin());
        assertEquals(actual.getMax(), expected.getMax());
        assertEquals(actual.getCount(), expected.getCount());
    }
}
