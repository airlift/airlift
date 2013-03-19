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
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.TestingRequestFilter;
import com.proofpoint.http.client.netty.NettyAsyncHttpClient;
import com.proofpoint.http.client.netty.NettyAsyncHttpClientConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import static com.google.common.io.Resources.getResource;

public class TestPoolingNettyHttpsClient
        extends AbstractHttpClientTest
{
    private static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
    private String originalTrustStore;
    private NettyIoPool ioPool;
    private NettyAsyncHttpClient httpClient;

    TestPoolingNettyHttpsClient()
    {
        super("localhost", getResource("localhost.keystore").toString());
    }

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        originalTrustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE, getResource("localhost.keystore").getPath());
        ioPool = new NettyIoPool();
        httpClient = new NettyAsyncHttpClient("test",
                ioPool,
                new HttpClientConfig(),
                new NettyAsyncHttpClientConfig().setEnableConnectionPooling(true),
                ImmutableSet.<HttpRequestFilter>of(new TestingRequestFilter()));
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        httpClient.close();
        if (originalTrustStore != null) {
            System.setProperty(JAVAX_NET_SSL_TRUST_STORE, originalTrustStore);
        }
        else {
            System.clearProperty(JAVAX_NET_SSL_TRUST_STORE);
        }
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
        try (NettyAsyncHttpClient client = new NettyAsyncHttpClient(config, ioPool)) {
            return client.execute(request, responseHandler);
        }
    }
}
