/*
 * Copyright 2012 Facebook, Inc.
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
package com.proofpoint.http.client.netty;

import com.google.common.collect.ImmutableSet;
import com.proofpoint.http.client.AbstractHttpClientTest;
import com.proofpoint.http.client.AsyncHttpClient;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.TestingRequestFilter;
import com.proofpoint.http.client.netty.testing.TestingNettyAsyncHttpClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class TestPoolingNettyHttpClient
        extends AbstractHttpClientTest
{
    private AsyncHttpClient httpClient;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        httpClient = TestingNettyAsyncHttpClient.getClientForTesting(new HttpClientConfig(),
                new NettyAsyncHttpClientConfig().setEnableConnectionPooling(true),
                new NettyIoPoolConfig(),
                ImmutableSet.<HttpRequestFilter>of(new TestingRequestFilter()));
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        httpClient.close();
    }


    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return httpClient.execute(request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        try (AsyncHttpClient client = TestingNettyAsyncHttpClient.getClientForTesting(config,  new NettyAsyncHttpClientConfig(),  new NettyIoPoolConfig())) {
            return client.execute(request, responseHandler);
        }
    }
}
