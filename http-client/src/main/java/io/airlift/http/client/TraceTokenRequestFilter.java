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
package io.airlift.http.client;

import io.airlift.tracetoken.TraceTokenManager;

import javax.inject.Inject;

import static io.airlift.http.client.Request.Builder.fromRequest;
import static java.util.Objects.requireNonNull;

public class TraceTokenRequestFilter
        implements HttpRequestFilter
{
    public static final String TRACETOKEN_HEADER = "X-Airlift-Tracetoken";
    private final TraceTokenManager traceTokenManager;

    @Inject
    public TraceTokenRequestFilter(TraceTokenManager traceTokenManager)
    {
        this.traceTokenManager = requireNonNull(traceTokenManager, "traceTokenManager is null");
    }

    @Override
    public Request filterRequest(Request request)
    {
        requireNonNull(request, "request is null");

        String token = traceTokenManager.getCurrentRequestToken();
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
        TraceTokenRequestFilter o = (TraceTokenRequestFilter) obj;
        return traceTokenManager.equals(o.traceTokenManager);
    }

    @Override
    public int hashCode()
    {
        return traceTokenManager.hashCode();
    }
}
