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

import java.io.Closeable;

import static java.util.Objects.requireNonNull;

public class SetThreadName
        implements Closeable
{
    private final String originalThreadName;

    public SetThreadName(String threadNamePrefix)
    {
        requireNonNull(threadNamePrefix, "threadNamePrefix is null");
        originalThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(threadNamePrefix + "-" + Thread.currentThread().threadId());
    }

    @Override
    public void close()
    {
        Thread.currentThread().setName(originalThreadName);
    }
}
