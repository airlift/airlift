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
package io.airlift.http.client.netty;

import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.TestingRequestFilter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestNettyHttpClient
        extends AbstractHttpClientTest
{
    private NettyIoPool ioPool;
    private NettyAsyncHttpClient httpClient;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        ioPool = new NettyIoPool("test");
        httpClient = new NettyAsyncHttpClient("test", ioPool, new HttpClientConfig(), new NettyAsyncHttpClientConfig(), ImmutableSet.<HttpRequestFilter>of(new TestingRequestFilter()));
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        ioPool.close();
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
        try (NettyAsyncHttpClient client = new NettyAsyncHttpClient("test", config, ioPool)) {
            return client.execute(request, responseHandler);
        }
    }

    @Test(enabled = false, description = "This Netty client does reuse connections")
    @Override
    public void testKeepAlive()
            throws Exception
    {
        super.testKeepAlive();
    }
}
