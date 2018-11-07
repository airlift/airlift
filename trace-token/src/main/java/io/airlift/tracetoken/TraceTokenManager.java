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
package io.airlift.tracetoken;

import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static java.util.Locale.ENGLISH;
import static java.util.UUID.randomUUID;

public class TraceTokenManager
{
    private final String prefix = randomUUID().toString().toLowerCase(ENGLISH).replace("-", "");
    private final AtomicLong sequence = new AtomicLong();

    private final ThreadLocal<String> token = new ThreadLocal<>();

    public void registerRequestToken(String token)
    {
        this.token.set(token);
    }

    public String getCurrentRequestToken()
    {
        return this.token.get();
    }

    public String createAndRegisterNewRequestToken()
    {
        String newToken = prefix + format("%010x", sequence.getAndIncrement());
        this.token.set(newToken);

        return newToken;
    }
}
