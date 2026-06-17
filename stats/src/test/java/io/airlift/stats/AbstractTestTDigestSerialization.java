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

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip tests that every serialization format must pass. A subclass supplies the encoder;
 * {@link TDigest#deserialize(Slice)} recognizes the format from the data.
 */
public abstract class AbstractTestTDigestSerialization
{
    protected abstract Slice serialize(TDigest digest);

    @Test
    public void testEmpty()
    {
        TDigest digest = new TDigest();
        TDigest deserialized = TDigest.deserialize(serialize(digest));

        assertSimilar(deserialized, digest);

        // ensure the internal arrays are initialized properly
        deserialized.add(10);
        assertThat(deserialized.getCount()).isEqualTo(1.0);
        assertThat(deserialized.valueAt(0.5)).isEqualTo(10.0);
    }

    @Test
    public void testEmptyIsExact()
    {
        assertExactRoundTrip(new TDigest());
        assertExactRoundTrip(new TDigest(50));
    }

    @Test
    public void testSingle()
    {
        TDigest digest = new TDigest();
        digest.add(1);

        TDigest deserialized = TDigest.deserialize(serialize(digest));

        assertSimilar(deserialized, digest);
        assertThat(deserialized.valueAt(0)).isEqualTo(digest.valueAt(0));
        assertThat(deserialized.valueAt(1)).isEqualTo(digest.valueAt(1));
    }

    @Test
    public void testSingleValueEdgeCases()
    {
        double[] values = {0.0, -0.0, 1, -1, 3.14159, -2.5e300, Double.MIN_VALUE, Double.MAX_VALUE};
        for (double value : values) {
            for (double weight : new double[] {1, 7, 127, 128, 0.5, 0x1p56, 0x1p63, 1e20}) {
                for (double compression : new double[] {TDigest.DEFAULT_COMPRESSION, 50}) {
                    TDigest digest = new TDigest(compression);
                    digest.add(value, weight);
                    assertExactRoundTrip(digest);
                }
            }
        }
    }

    @Test
    public void testComplex()
    {
        TDigest digest = new TDigest();
        addAll(digest, asList(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 5, 6, 7));

        assertRoundTrip(digest);
    }

    @Test
    public void testNegativeAndLargeValues()
    {
        TDigest digest = new TDigest();
        addAll(digest, asList(-1_000_000, -5, -1, 0, 1, 5, 1_000_000));

        assertRoundTrip(digest);
    }

    @Test
    public void testFractionalWeights()
    {
        TDigest digest = new TDigest();
        digest.add(1, 0.5);
        digest.add(2, 1.25);
        digest.add(3, 99999.999999999);

        assertRoundTrip(digest);
    }

    @Test
    public void testIdenticalValues()
    {
        // unit-weight centroids of one value do not merge at these sizes, so the digest keeps every
        // centroid, with nothing to encode for the mean deltas and a single bit for each weight
        for (int count = 1; count <= 64; count++) {
            TDigest digest = new TDigest();
            for (int i = 0; i < count; i++) {
                digest.add(5);
            }
            assertExactRoundTrip(digest);
        }
    }

    @Test
    public void testMergedDigest()
    {
        TDigest digest = new TDigest();
        digest.add(5, 10);
        TDigest other = new TDigest();
        other.add(-3);
        other.add(100);
        digest.mergeWith(other);

        assertExactRoundTrip(digest);
    }

    @Test
    public void testWideMeanDeltas()
    {
        // the deltas from -1 to 0 and from 0 to 1 are 62 bits wide and sit in a full group
        TDigest digest = new TDigest();
        for (int value = -1; value <= 16; value++) {
            digest.add(value);
        }
        assertExactRoundTrip(digest);
    }

    @Test
    public void testTotalWeightDiffersFromWeightSum()
    {
        // totalWeight sums in insertion order and the centroid weights in mean order, so the two differ in the last bit
        TDigest digest = new TDigest();
        digest.add(3, 0.3);
        digest.add(2, 0.2);
        digest.add(1, 0.1);
        assertExactRoundTrip(digest);
    }

    @Test
    public void testSignedZeroMinMax()
    {
        // min and max stay 0.0 while one end mean is -0.0
        TDigest digest = new TDigest();
        digest.add(0.0);
        digest.add(-0.0);
        assertExactRoundTrip(digest);
    }

    @Test
    public void testSignedZeroMinFromMerge()
    {
        // mergeWith takes min -0.0 from the other digest while the first mean stays 0.0
        TDigest digest = new TDigest();
        digest.add(0.0);
        TDigest other = new TDigest();
        other.add(-0.0);
        other.add(1.0);
        digest.mergeWith(other);
        assertExactRoundTrip(digest);
    }

    @Test
    public void testMeanAndWeightMagnitudes()
    {
        // consecutive means a single ulp apart and binades apart, weights straddling every power of two
        TDigest digest = new TDigest();
        for (int shift = 0; shift < 63; shift += 7) {
            digest.add(Math.scalb(1.0, shift), Math.max(1, Math.scalb(1.0, shift) - 1));
            digest.add(Math.scalb(1.0, shift) + Math.ulp(Math.scalb(1.0, shift)), Math.scalb(1.0, shift));
        }
        digest.add(-Double.MAX_VALUE);
        digest.add(Double.MAX_VALUE);

        assertExactRoundTrip(digest);
    }

    @RepeatedTest(1000)
    public void testRandom()
    {
        TDigest digest = new TDigest();

        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            values.add(ThreadLocalRandom.current().nextInt());
        }

        addAll(digest, values);

        assertRoundTrip(digest);
    }

    @Test
    public void testDeserializeFromSlicedView()
    {
        TDigest digest = new TDigest();
        for (int i = 0; i < 1000; i++) {
            digest.add(i * 1.5, 3);
        }
        Slice serialized = serialize(digest);

        // a view at the very end of a larger buffer: no read may run past the backing array
        Slice buffer = Slices.allocate(serialized.length() + 5);
        buffer.setBytes(5, serialized);
        TDigest deserialized = TDigest.deserialize(buffer.slice(5, serialized.length()));
        assertThat(deserialized.serializeLegacy()).isEqualTo(digest.serializeLegacy());

        // a view with trailing bytes beyond the digest must not be confused by them
        Slice padded = Slices.allocate(serialized.length() + 16);
        padded.setBytes(0, serialized);
        byte[] garbage = new byte[16];
        Arrays.fill(garbage, (byte) 0xFF);
        padded.setBytes(serialized.length(), garbage);
        deserialized = TDigest.deserialize(padded.slice(0, serialized.length()));
        assertThat(deserialized.serializeLegacy()).isEqualTo(digest.serializeLegacy());
    }

    @Test
    public void testDeserializeTruncated()
    {
        TDigest digest = new TDigest();
        for (int i = 0; i < 100; i++) {
            digest.add(i);
        }
        Slice serialized = serialize(digest);
        for (int length = 2; length < serialized.length(); length += 7) {
            Slice truncated = serialized.slice(0, length);
            assertThatThrownBy(() -> TDigest.deserialize(truncated))
                    .isInstanceOfAny(IllegalArgumentException.class, IndexOutOfBoundsException.class);
        }
    }

    // the format is lossless, so the reconstructed quantiles must match exactly
    protected void assertRoundTrip(TDigest digest)
    {
        TDigest deserialized = TDigest.deserialize(serialize(digest));

        assertSimilar(deserialized, digest);

        for (double quantile = 0; quantile <= 1; quantile += 0.1) {
            assertThat(deserialized.valueAt(quantile)).isEqualTo(digest.valueAt(quantile));
        }
    }

    // the legacy encoding is a plain dump of the state, so comparing it compares the digests bit for bit
    protected void assertExactRoundTrip(TDigest digest)
    {
        TDigest deserialized = TDigest.deserialize(serialize(digest));
        assertThat(deserialized.serializeLegacy()).isEqualTo(digest.serializeLegacy());
    }

    protected static void addAll(TDigest digest, List<Integer> values)
    {
        for (int value : values) {
            digest.add(value);
        }
    }

    protected static void assertSimilar(TDigest actual, TDigest expected)
    {
        assertThat(actual.getMin()).isEqualTo(expected.getMin(), Offset.offset(0.0));
        assertThat(actual.getMax()).isEqualTo(expected.getMax(), Offset.offset(0.0));
        assertThat(actual.getCount()).isEqualTo(expected.getCount(), Offset.offset(0.0));
    }
}
