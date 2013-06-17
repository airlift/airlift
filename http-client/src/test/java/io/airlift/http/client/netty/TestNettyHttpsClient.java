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
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.TestingRequestFilter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.URI;

import static com.google.common.io.Resources.getResource;
import static io.airlift.http.client.Request.Builder.prepareGet;

public class TestNettyHttpsClient
        extends AbstractHttpClientTest
{
    private static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
    private String originalTrustStore;
    private NettyIoPool ioPool;
    private NettyAsyncHttpClient httpClient;

    TestNettyHttpsClient()
    {
        super("localhost", getResource("localhost.keystore").toString());
    }

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        originalTrustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE, getResource("localhost.keystore").getPath());
        this.ioPool = new NettyIoPool("test");
        this.httpClient = new NettyAsyncHttpClient("test", ioPool, new HttpClientConfig(), new NettyAsyncHttpClientConfig(), ImmutableSet.of(new TestingRequestFilter()));
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        httpClient.close();
        ioPool.close();
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

    @Test(expectedExceptions = SSLHandshakeException.class)
    public void testCertHostnameMismatch()
            throws Exception
    {
        URI uri = new URI("https", null, "127.0.0.1", baseURI.getPort(), "/", null, null);
        Request request = prepareGet()
                .setUri(uri)
                .build();

        executeRequest(request, new ResponseToStringHandler());
    }
}
