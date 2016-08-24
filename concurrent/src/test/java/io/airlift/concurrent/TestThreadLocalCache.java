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

import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

public class TestThreadLocalCache
{
    @Test
    public void testSanity()
            throws Exception
    {
        AtomicInteger count = new AtomicInteger(0);
        ThreadLocalCache<String, String> cache = new ThreadLocalCache<>(2, key -> {
            // Concatenate key with counter
            return key + count.getAndAdd(1);
        });

        // Load first key
        assertEquals(cache.get("abc"), "abc0");
        assertEquals(cache.get("abc"), "abc0");

        // Load second key
        assertEquals(cache.get("def"), "def1");

        // First key should still be there
        assertEquals(cache.get("abc"), "abc0");

        // Expire first key by exceeding max size
        assertEquals(cache.get("ghi"), "ghi2");

        // First key should now be regenerated
        assertEquals(cache.get("abc"), "abc3");

        // TODO: add tests for multiple threads
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "loader returned null value")
    public void testDisallowsNulls()
    {
        new ThreadLocalCache<>(10, key -> null).get("foo");
    }
}
