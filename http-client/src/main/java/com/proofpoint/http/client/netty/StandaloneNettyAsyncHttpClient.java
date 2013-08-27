package com.proofpoint.http.client.netty;

import com.google.common.io.Closeables;
import com.proofpoint.http.client.AsyncHttpClient;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;

import java.util.Collections;
import java.util.Set;

/**
 * Standalone variant of the {@link NettyAsyncHttpClient} which manages the thread pool itself.
 */
public class StandaloneNettyAsyncHttpClient
        implements AsyncHttpClient
{
    private final NettyIoPool ioPool;
    private final NettyAsyncHttpClient httpClient;

    public StandaloneNettyAsyncHttpClient(String name)
    {
        this(name, new HttpClientConfig());
    }

    public StandaloneNettyAsyncHttpClient(String name, HttpClientConfig httpClientConfig)
    {
        this(name, httpClientConfig, new NettyAsyncHttpClientConfig(), new NettyIoPoolConfig(), Collections.<HttpRequestFilter>emptySet());
    }

    public StandaloneNettyAsyncHttpClient(
            String name,
            HttpClientConfig httpClientConfig,
            NettyAsyncHttpClientConfig asyncHttpClientConfig,
            NettyIoPoolConfig ioPoolConfig,
            Set<? extends HttpRequestFilter> filters)
    {
        this.ioPool = new NettyIoPool(name, ioPoolConfig);
        this.httpClient = new NettyAsyncHttpClient(name, ioPool, httpClientConfig, asyncHttpClientConfig, filters);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void close()
    {
        Closeables.closeQuietly(httpClient);
        Closeables.closeQuietly(ioPool);
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        return httpClient.execute(request, responseHandler);
    }

    @Override
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    @Override
    public <T, E extends Exception> AsyncHttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        return httpClient.executeAsync(request, responseHandler);
    }
}
