/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
package com.proofpoint.event.client;

import com.google.common.util.concurrent.CheckedFuture;

import java.io.IOException;
import java.util.concurrent.Future;

public interface EventClient
{
    <T> CheckedFuture<Void, RuntimeException> post(T... event)
            throws IllegalArgumentException;

    <T> CheckedFuture<Void, RuntimeException> post(Iterable<T> events)
            throws IllegalArgumentException;

    <T> CheckedFuture<Void, RuntimeException> post(EventGenerator<T> eventGenerator)
            throws IllegalArgumentException;

    public interface EventGenerator<T>
    {
        void generate(EventPoster<T> eventPoster)
                throws IOException;
    }

    public interface EventPoster<T>
    {
        void post(T event)
                throws IOException;
    }
}
