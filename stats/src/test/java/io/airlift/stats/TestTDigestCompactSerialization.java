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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestTDigestCompactSerialization
        extends AbstractTestTDigestSerialization
{
    @Override
    protected Slice serialize(TDigest digest)
    {
        return digest.serializeCompact();
    }

    @Test
    public void testSmallerForIntegerData()
    {
        TDigest digest = new TDigest();
        for (int i = 0; i < 10_000; i++) {
            digest.add(i);
        }

        assertThat(digest.serializeCompact().length()).isLessThan(digest.serializedSizeInBytes() * 2 / 3);
    }

    @Test
    public void testSingleValueIsSmall()
    {
        TDigest digest = new TDigest();
        digest.add(1_234_567);

        // tag + flags + count + raw mean + width byte + the 1-bit weight in one byte
        assertThat(digest.serializeCompact().length()).isEqualTo(13);
        assertRoundTrip(digest);
    }

    @Test
    public void testWorstCaseIsBoundedByLegacyPlusGroupWidths()
    {
        // means 16 binades apart need full-width deltas and fractional weights cannot be integers;
        // the merge keeps both Double.MAX_VALUE values as end centroids and the weights sum exactly, so
        // the header holds only the non-default compression (flags 0xc)
        TDigest digest = new TDigest(50);
        for (int i = -60; i <= 60; i++) {
            digest.add(Math.scalb(1.0, 16 * i), 0.5);
        }
        TDigest other = new TDigest(50);
        other.add(-Double.MAX_VALUE, 0.25);
        other.add(Double.MAX_VALUE, 0.25);
        digest.mergeWith(other);

        // a full header is as long as the legacy one; each section adds a width byte per group of eight
        int centroids = (digest.serializeLegacy().length() - 37) / 16;
        assertThat(digest.serializeCompact().length()).isLessThanOrEqualTo(digest.serializeLegacy().length() + centroids + 1);
        assertExactRoundTrip(digest);
    }

    @Test
    public void testRejectsUnknownFlags()
    {
        TDigest digest = new TDigest();
        digest.add(42);

        // a flag bit this version does not define could add or omit a section, so it must not be decoded
        for (int unknownFlag : new int[] {0x10, 0x40, 0x80}) {
            byte[] bytes = digest.serializeCompact().getBytes();
            bytes[1] |= (byte) unknownFlag;
            assertThatThrownBy(() -> TDigest.deserialize(Slices.wrappedBuffer(bytes)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid format: unknown flags");
        }
    }
}
