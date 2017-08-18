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

/**
 * Interface designed to capture a lambda producing a value and throwing a checked exception.
 *
 * @param <T> type of the value produced by the captured lambda
 * @param <E> type of exception thrown by the captured lambda
 */
@FunctionalInterface
public interface CheckedCallable<T, E extends Throwable>
{
    T call()
            throws E;
}
