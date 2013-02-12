package com.proofpoint.http.client.netty;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.Duration;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.jboss.netty.channel.Channels.pipeline;

public class HttpClientPipelineFactory
        implements ChannelPipelineFactory
{
    private final Timer timer = new HashedWheelTimer();
    private final OrderedMemoryAwareThreadPoolExecutor executor;
    private final ChannelHandler timeoutHandler;
    private final AtomicReference<NettyConnectionPool> nettyConnectionPoolReference = new AtomicReference<>();
    private final int maxContentLength;

    public HttpClientPipelineFactory(OrderedMemoryAwareThreadPoolExecutor executor, Duration readTimeout, DataSize maxContentLength)
    {
        this.executor = executor;
        this.timeoutHandler = new ReadTimeoutHandler(timer, (long) readTimeout.toMillis(), TimeUnit.MILLISECONDS);
        this.maxContentLength = Ints.saturatedCast(maxContentLength.toBytes());
    }

    public ChannelPipeline getPipeline()
            throws Exception
    {
        NettyConnectionPool nettyConnectionPool = nettyConnectionPoolReference.get();
        Preconditions.checkState(nettyConnectionPool != null, "NettyConnectionPool has not been set");

        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

        // timeout read requests
        pipeline.addLast("timeout", timeoutHandler);

        // read and write http messages
        pipeline.addLast("codec", new HttpClientCodec());

        // decompress gzip responses
        pipeline.addLast("inflater", new HttpContentDecompressor());

        // gather all chunks into a single http message
        pipeline.addLast("aggregator", new HttpChunkAggregator(maxContentLength));

        // move response handler to user worker pool
        pipeline.addLast("pipelineExecutor", new ExecutionHandler(executor));

        // response handler
        pipeline.addLast("handler", new NettyHttpResponseChannelHandler(nettyConnectionPool));

        return pipeline;
    }

    public void setNettyConnectionPool(NettyConnectionPool nettyConnectionPool)
    {
        if (!this.nettyConnectionPoolReference.compareAndSet(null, nettyConnectionPool)) {
            throw new IllegalStateException("NettyConnectionPool already set");
        }
    }
}
