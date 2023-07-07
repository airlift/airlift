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

    public SetThreadName(String format, Object... args)
    {
        requireNonNull(format, "format is null");
        originalThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(String.format(format, args) + "-" + getThreadId(Thread.currentThread()));
    }

    @SuppressWarnings("deprecation")
    public static long getThreadId(Thread thread)
    {
        // TODO: thread.threadId() when moved to JDK 21
        return thread.getId();
    }

    @Override
    public void close()
    {
        Thread.currentThread().setName(originalThreadName);
    }
}
