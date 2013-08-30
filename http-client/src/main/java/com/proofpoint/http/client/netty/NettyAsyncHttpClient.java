package com.proofpoint.http.client.netty;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.concurrent.ThreadPoolExecutorMBean;
import com.proofpoint.http.client.AsyncHttpClient;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.netty.NettyConnectionPool.ConnectionCallback;
import com.proofpoint.http.client.netty.NettyResponseFuture.NettyAsyncHttpState;
import com.proofpoint.http.client.netty.socks.Socks4ClientBootstrap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.DynamicChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.HashedWheelTimer;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Nested;

import javax.annotation.PreDestroy;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Beta
public class NettyAsyncHttpClient
        implements AsyncHttpClient
{
    private final RequestStats stats = new RequestStats();
    private final List<HttpRequestFilter> requestFilters;

    private final OrderedMemoryAwareThreadPoolExecutor executor;
    private final ThreadPoolExecutorMBean executorMBean;
    private final NettyConnectionPool nettyConnectionPool;
    private final HashedWheelTimer timer;

    public NettyAsyncHttpClient(String name, HttpClientConfig config, NettyIoPool ioPool)
    {
        this(name, ioPool, config, new NettyAsyncHttpClientConfig(), Collections.<HttpRequestFilter>emptySet());
    }

    public NettyAsyncHttpClient(String name,
            NettyIoPool ioPool,
            HttpClientConfig config,
            NettyAsyncHttpClientConfig asyncConfig,
            Set<? extends HttpRequestFilter> requestFilters)
    {
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(ioPool, "ioPool is null");
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(asyncConfig, "asyncConfig is null");
        Preconditions.checkNotNull(requestFilters, "requestFilters is null");

        this.requestFilters = ImmutableList.copyOf(requestFilters);

        String namePrefix = "http-client-" + name;

        // shared timer for channel factory and read timeout channel handler
        this.timer = new HashedWheelTimer(new ThreadFactoryBuilder().setNameFormat(namePrefix + "-timer-%s").setDaemon(true).build());

        ChannelFactory channelFactory = new NioClientSocketChannelFactory(ioPool.getBossPool(), ioPool.getWorkerPool());

        ThreadFactory workerThreadFactory = new ThreadFactoryBuilder().setNameFormat(namePrefix + "-worker-%s").setDaemon(true).build();
        this.executor = new OrderedMemoryAwareThreadPoolExecutor(asyncConfig.getWorkerThreads(), 0, 0, 30, TimeUnit.SECONDS, workerThreadFactory);
        this.executorMBean = new ThreadPoolExecutorMBean(executor);

        ClientBootstrap bootstrap;
        if (config.getSocksProxy() == null) {
            bootstrap = new ClientBootstrap(channelFactory);
        }
        else {
            bootstrap = new Socks4ClientBootstrap(channelFactory, config.getSocksProxy());
        }
        bootstrap.setOption("connectTimeoutMillis", config.getConnectTimeout().toMillis());
        bootstrap.setOption("soLinger", 0);

        nettyConnectionPool = new NettyConnectionPool(bootstrap,
                config.getMaxConnections(),
                executor,
                asyncConfig.isEnableConnectionPooling());

        HttpClientPipelineFactory pipelineFactory = new HttpClientPipelineFactory(nettyConnectionPool, timer, executor, config.getReadTimeout(), asyncConfig.getMaxContentLength());
        bootstrap.setPipelineFactory(pipelineFactory);
    }

    public List<HttpRequestFilter> getRequestFilters()
    {
        return requestFilters;
    }

    @SuppressWarnings("deprecation")
    @PreDestroy
    @Override
    public void close()
    {
        try {
            executor.shutdownNow();
        }
        catch (Exception e) {
            // ignored
        }

        Closeables.closeQuietly(nettyConnectionPool);

        try {
            timer.stop();
        }
        catch (Exception e) {
            // ignored
        }
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        try {
            return executeAsync(request, responseHandler).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
        catch (ExecutionException e) {
            Throwables.propagateIfPossible(e.getCause());

            if (e.getCause() instanceof Exception) {
                // the HTTP client and ResponseHandler interface enforces this
                throw (E) e.getCause();
            }

            // e.getCause() is some direct subclass of throwable
            throw Throwables.propagate(e.getCause());
        }
    }

    @Flatten
    @Override
    public RequestStats getStats()
    {
        return stats;
    }

    @Nested
    public ThreadPoolExecutorMBean getExecutor()
    {
        return executorMBean;
    }

    @Override
    public <T, E extends Exception> AsyncHttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        // process the request through the filters
        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }

        Preconditions.checkArgument("http".equalsIgnoreCase(request.getUri().getScheme()) || "https".equalsIgnoreCase(request.getUri().getScheme()),
                "%s only supports http and https requests", getClass().getSimpleName());

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
            DynamicChannelBuffer content = new DynamicChannelBuffer(64 * 1024);
            ChannelBufferOutputStream out = new ChannelBufferOutputStream(content);
            bodyGenerator.write(out);

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
            nettyResponseFuture.setState(NettyAsyncHttpState.SENDING_REQUEST);

            // add the response handler to the channel object, so we can notify caller when request is complete
            channel.getPipeline().getContext(NettyHttpResponseChannelHandler.class).setAttachment(nettyResponseFuture);

            HttpRequest nettyRequest = buildNettyHttpRequest(request);
            channel.write(nettyRequest).addListener(new ChannelFutureListener()
            {
                @Override
                public void operationComplete(ChannelFuture future)
                        throws Exception
                {
                    if (future.isSuccess()) {
                        nettyResponseFuture.setState(NettyAsyncHttpState.WAITING_FOR_RESPONSE);
                    }
                    else if (future.isCancelled()) {
                        nettyResponseFuture.failed(new CanceledRequestException());
                    }
                    else {
                        Throwable cause = future.getCause();
                        if (cause == null) {
                            cause = new UnknownRequestException();
                        }
                        nettyResponseFuture.failed(cause);
                    }
                }
            });
        }

        @Override
        public void onError(Throwable throwable)
        {
            nettyResponseFuture.failed(throwable);
        }
    }
}
