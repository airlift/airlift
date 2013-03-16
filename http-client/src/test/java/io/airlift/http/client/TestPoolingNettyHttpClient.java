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
package io.airlift.http.client;

import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.netty.NettyAsyncHttpClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class TestPoolingNettyHttpClient
        extends AbstractHttpClientTest
{
    private NettyAsyncHttpClient httpClient;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        httpClient = new NettyAsyncHttpClient("test",
                new HttpClientConfig(),
                new NettyAsyncHttpClientConfig().setEnableConnectionPooling(true),
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
        try (NettyAsyncHttpClient client = new NettyAsyncHttpClient(config)) {
            return client.execute(request, responseHandler);
        }
    }
}
