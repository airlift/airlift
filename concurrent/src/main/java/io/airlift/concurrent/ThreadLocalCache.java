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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Provides a ThreadLocal cache with a maximum cache size per thread.
 * Values must not be null.
 *
 * @param <K> cache key type
 * @param <V> cache value type
 */
public final class ThreadLocalCache<K, V>
{
    @SuppressWarnings("ThreadLocalNotStaticFinal")
    private final ThreadLocal<Map<K, V>> cache;
    private final Function<K, V> loader;

    public ThreadLocalCache(int maxSizePerThread, Function<K, V> loader)
    {
        checkArgument(maxSizePerThread > 0, "max size must be greater than zero");
        this.cache = ThreadLocal.withInitial(() -> new BoundedMap<K, V>(maxSizePerThread));
        this.loader = requireNonNull(loader, "loader is null");
    }

    public V get(K key)
    {
        V value = cache.get().computeIfAbsent(key, loader);
        return requireNonNull(value, "loader returned null value");
    }

    @SuppressWarnings("CloneableClassWithoutClone")
    private static class BoundedMap<K, V>
            extends LinkedHashMap<K, V>
    {
        private final int maxSize;

        public BoundedMap(int maxSize)
        {
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
        {
            return size() > maxSize;
        }
    }
}
