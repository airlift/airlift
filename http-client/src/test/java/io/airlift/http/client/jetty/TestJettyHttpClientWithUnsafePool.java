package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;

import static io.airlift.http.client.HttpClientConfig.HttpBufferPoolType.UNSAFE;

public class TestJettyHttpClientWithUnsafePool
        extends TestJettyHttpClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttpBufferPoolType(UNSAFE);
    }
}
