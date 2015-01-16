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

import javax.annotation.Nullable;
import java.util.UUID;

public class TraceTokenManager
{
    private static final ThreadLocal<String> token = new ThreadLocal<>();

    /**
     * @deprecated This is now a utility class. Instantiation is no longer required.
     */
    @Deprecated
    public TraceTokenManager()
    {}

    public static void registerRequestToken(@Nullable String token)
    {
        TraceTokenManager.token.set(token);
    }

    public static String getCurrentRequestToken()
    {
        return token.get();
    }

    public static String createAndRegisterNewRequestToken()
    {
        String newToken = UUID.randomUUID().toString();
        token.set(newToken);

        return newToken;
    }
}
