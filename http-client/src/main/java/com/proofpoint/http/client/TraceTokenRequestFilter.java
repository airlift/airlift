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
package com.proofpoint.http.client;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.http.client.Request.Builder.fromRequest;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;

public class TraceTokenRequestFilter
        implements HttpRequestFilter
{
    public static final String TRACETOKEN_HEADER = "X-Proofpoint-Tracetoken";

    @Override
    public Request filterRequest(Request request)
    {
        checkNotNull(request, "request is null");

        String token = getCurrentRequestToken();
        if (token == null) {
            return request;
        }

        return fromRequest(request)
                .addHeader(TRACETOKEN_HEADER, token)
                .build();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        return 1;
    }
}
