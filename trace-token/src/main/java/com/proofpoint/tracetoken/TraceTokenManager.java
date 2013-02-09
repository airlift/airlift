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
package com.proofpoint.tracetoken;

import com.proofpoint.log.Logging;

import java.util.UUID;

final public class TraceTokenManager
{
    private static final String TRACE_TOKEN = "TraceToken";
    private final ThreadLocal<String> token = new ThreadLocal<>();

    public void registerRequestToken(String token)
    {
        this.token.set(token);
        Logging.putMDC(TRACE_TOKEN, token);
    }

    public String getCurrentRequestToken()
    {
        return this.token.get();
    }

    public String createAndRegisterNewRequestToken()
    {
        String newToken = UUID.randomUUID().toString();
        registerRequestToken(newToken);

        return newToken;
    }

    public void clearRequestToken()
    {
        this.token.remove();
        Logging.removeMDC(TRACE_TOKEN);
    }
}
