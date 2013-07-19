package io.airlift.http.client.netty;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.Timer;

import java.util.concurrent.TimeUnit;

import static org.jboss.netty.channel.Channels.pipeline;

public class HttpClientPipelineFactory
        implements ChannelPipelineFactory
{
    private final Timer timer;
    private final OrderedMemoryAwareThreadPoolExecutor executor;
    private final ReadTimeoutHandler timeoutHandler;
    private final NettyConnectionPool nettyConnectionPool;
    private final int maxContentLength;

    public HttpClientPipelineFactory(NettyConnectionPool nettyConnectionPool,
            Timer timer,
            OrderedMemoryAwareThreadPoolExecutor executor,
            Duration readTimeout,
            DataSize maxContentLength)
    {
        this.timer = timer;
        Preconditions.checkNotNull(nettyConnectionPool, "nettyConnectionPool is null");
        Preconditions.checkNotNull(executor, "executor is null");
        Preconditions.checkNotNull(readTimeout, "readTimeout is null");
        Preconditions.checkNotNull(maxContentLength, "maxContentLength is null");

        this.nettyConnectionPool = nettyConnectionPool;
        this.executor = executor;
        this.timeoutHandler = new ReadTimeoutHandler(this.timer, readTimeout.toMillis(), TimeUnit.MILLISECONDS);
        this.maxContentLength = Ints.checkedCast(maxContentLength.toBytes());
    }

    public ChannelPipeline getPipeline()
            throws Exception
    {
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
}
