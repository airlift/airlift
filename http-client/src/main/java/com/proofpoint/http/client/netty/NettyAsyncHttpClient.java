package com.proofpoint.http.client.netty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import com.proofpoint.http.client.AsyncHttpClient;
import com.proofpoint.http.client.AsyncHttpClientConfig;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.netty.NettyConnectionPool.ConnectionCallback;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names;

public class NettyAsyncHttpClient
        implements AsyncHttpClient
{
    private final RequestStats stats = new RequestStats();
    private final List<HttpRequestFilter> requestFilters;

    private final OrderedMemoryAwareThreadPoolExecutor executor;
    private final NettyConnectionPool nettyConnectionPool;

    public NettyAsyncHttpClient()
    {
        this(new HttpClientConfig());
    }

    public NettyAsyncHttpClient(HttpClientConfig config)
    {
        this(config, new AsyncHttpClientConfig(), Collections.<HttpRequestFilter>emptySet());
    }

    public NettyAsyncHttpClient(HttpClientConfig config, AsyncHttpClientConfig asyncConfig, Set<? extends HttpRequestFilter> requestFilters)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(asyncConfig, "asyncConfig is null");
        Preconditions.checkNotNull(requestFilters, "requestFilters is null");

        this.requestFilters = ImmutableList.copyOf(requestFilters);

        ChannelFactory channelFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

        executor = new OrderedMemoryAwareThreadPoolExecutor(asyncConfig.getWorkerThreads(), 0, 0);

        HttpClientPipelineFactory pipelineFactory = new HttpClientPipelineFactory(executor, config.getReadTimeout(), asyncConfig.getMaxContentLength());

        nettyConnectionPool = new NettyConnectionPool(channelFactory,
                pipelineFactory,
                config.getConnectTimeout(),
                config.getMaxConnections(),
                executor,
                asyncConfig.isEnableConnectionPooling());

        // give a the pipeline factory a reference to the connection pool so it can return connections when the request is complete
        pipelineFactory.setNettyConnectionPool(nettyConnectionPool);
    }

    @Override
    public void close()
    {
        try {
            nettyConnectionPool.close();
        }
        finally {
            executor.shutdownNow(true);
        }
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        return executeAsync(request, responseHandler).checkedGet();
    }

    @Override
    public RequestStats getStats()
    {
        return stats;
    }

    @Override
    public <T, E extends Exception> CheckedFuture<T, E> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        // process the request through the filters
        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }

        Preconditions.checkArgument("http".equalsIgnoreCase(request.getUri().getScheme()), "%s only supports http requests", getClass().getSimpleName());

        // create a future for the caller
        NettyResponseFuture<T, E> nettyResponseFuture = new NettyResponseFuture<>(request, responseHandler, stats);

        // schedule the request with a connection
        nettyConnectionPool.execute(request.getUri(), new HttpConnectionCallback<>(request, nettyResponseFuture));

        // return caller's future
        return nettyResponseFuture;
    }

    @VisibleForTesting
    public static HttpRequest buildNettyHttpRequest(Request request)
            throws Exception
    {
        //
        // http request path
        URI uri = request.getUri();
        StringBuilder pathBuilder = new StringBuilder(100);
        // path part
        if (uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
            pathBuilder.append('/');
        }
        else {
            pathBuilder.append(uri.getRawPath());
        }
        // query
        if (uri.getRawQuery() != null) {
            pathBuilder.append('?').append(uri.getRawQuery());
        }
        // http clients should not send the #fragment

        //
        // set http request line
        HttpRequest nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod(request.getMethod()), pathBuilder.toString());

        //
        // set host header
        if (uri.getPort() == -1) {
            nettyRequest.setHeader(Names.HOST, uri.getHost());
        }
        else {
            nettyRequest.setHeader(Names.HOST, uri.getHost() + ":" + uri.getPort());
        }

        //
        // set user defined headers
        for (Entry<String, Collection<String>> header : request.getHeaders().asMap().entrySet()) {
            nettyRequest.setHeader(header.getKey(), header.getValue());
        }

        //
        // set body
        BodyGenerator bodyGenerator = request.getBodyGenerator();
        if (bodyGenerator != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            bodyGenerator.write(out);
            ChannelBuffer content = ChannelBuffers.copiedBuffer(out.toByteArray());

            nettyRequest.setHeader(Names.CONTENT_LENGTH, content.readableBytes());
            nettyRequest.setContent(content);
        }
        return nettyRequest;
    }

    private static class HttpConnectionCallback<T, E extends Exception>
            implements ConnectionCallback
    {
        private final Request request;
        private final NettyResponseFuture<T, E> nettyResponseFuture;

        public HttpConnectionCallback(Request request, NettyResponseFuture<T, E> nettyResponseFuture)
        {
            this.request = request;
            this.nettyResponseFuture = nettyResponseFuture;
        }

        @Override
        public void run(Channel channel)
                throws Exception
        {
            // add the response handler to the channel object, so we can notify caller when request is complete
            channel.getPipeline().getContext(NettyHttpResponseChannelHandler.class).setAttachment(nettyResponseFuture);
            
            HttpRequest nettyRequest = buildNettyHttpRequest(request);
            channel.write(nettyRequest);
        }

        @Override
        public void onError(Throwable throwable)
        {
            nettyResponseFuture.setException(throwable);
        }
    }
}
