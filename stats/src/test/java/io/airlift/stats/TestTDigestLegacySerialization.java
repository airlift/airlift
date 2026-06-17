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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTDigestLegacySerialization
        extends AbstractTestTDigestSerialization
{
    @Override
    protected Slice serialize(TDigest digest)
    {
        return digest.serialize();
    }

    @Test
    public void testSerializeWritesLegacyFormat()
    {
        TDigest digest = new TDigest();
        for (int i = 0; i < 10_000; i++) {
            digest.add(i);
        }

        // the default stays the format every deployed reader understands
        assertThat(digest.serialize()).isEqualTo(digest.serializeLegacy());
        assertThat(digest.serialize().length()).isEqualTo(digest.serializedSizeInBytes());
    }
}
