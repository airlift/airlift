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
package io.airlift.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class TestThreadLocalCache {
    @Test
    public void testSanity() {
        AtomicInteger count = new AtomicInteger(0);
        ThreadLocalCache<String, String> cache = new ThreadLocalCache<>(2, key -> {
            // Concatenate key with counter
            return key + count.getAndAdd(1);
        });

        // Load first key
        assertThat(cache.get("abc")).isEqualTo("abc0");
        assertThat(cache.get("abc")).isEqualTo("abc0");

        // Load second key
        assertThat(cache.get("def")).isEqualTo("def1");

        // First key should still be there
        assertThat(cache.get("abc")).isEqualTo("abc0");

        // Expire first key by exceeding max size
        assertThat(cache.get("ghi")).isEqualTo("ghi2");

        // First key should now be regenerated
        assertThat(cache.get("abc")).isEqualTo("abc3");

        // TODO: add tests for multiple threads
    }

    @Test
    public void testDisallowsNulls() {
        assertThatThrownBy(() -> new ThreadLocalCache<>(10, key -> null).get("foo"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageMatching("loader returned null value");
    }
}
