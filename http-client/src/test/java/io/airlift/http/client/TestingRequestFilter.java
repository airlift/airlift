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

import static io.airlift.http.client.Request.Builder.fromRequest;

public class TestingRequestFilter
        implements HttpRequestFilter
{
    @Override
    public Request filterRequest(Request request)
    {
        return fromRequest(request)
                .addHeader("x-custom-filter", "custom value")
                .build();
    }
}
