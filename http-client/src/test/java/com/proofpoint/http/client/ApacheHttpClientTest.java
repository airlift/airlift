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

import com.google.common.collect.ImmutableSet;

import org.testng.annotations.BeforeMethod;

public class ApacheHttpClientTest
        extends AbstractHttpClientTest
{
    private ApacheHttpClient httpClient;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        httpClient = new ApacheHttpClient(new HttpClientConfig(),
                ImmutableSet.<HttpRequestFilter>of(new TestingRequestFilter()));
    }

    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return httpClient.execute(request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T  executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        ApacheHttpClient client = new ApacheHttpClient(config);
        return client.execute(request, responseHandler);
    }
}
