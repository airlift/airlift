package io.airlift.http.client.netty;

import io.airlift.units.Duration;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;

public class NettyConnectionPool
{
    private final ChannelGroup openChannels = new DefaultChannelGroup("http-client");
    private final ClientBootstrap bootstrap;

    public NettyConnectionPool(ChannelFactory channelFactory, ChannelPipelineFactory pipelineFactory, Duration connectTimeout)
    {
        bootstrap = new ClientBootstrap(channelFactory);
        bootstrap.setPipelineFactory(pipelineFactory);
        bootstrap.setOption("connectTimeoutMillis", (long) connectTimeout.toMillis());
        bootstrap.setOption("soLinger", 0);
    }

    public void close()
    {
        openChannels.close();
        bootstrap.releaseExternalResources();
    }

    public ChannelFuture execute(URI uri, ConnectionCallback connectionCallback)
    {
        InetSocketAddress remoteAddress = new InetSocketAddress(uri.getHost(), uri.getPort());
        ChannelFuture future = bootstrap.connect(remoteAddress);
        future.addListener(new CallbackConnectionListener(remoteAddress, connectionCallback, openChannels));
        return future;
    }

    public void returnConnection(Channel channel)
    {
        if (channel != null) {
            channel.close();
        }
    }

    public void destroyConnection(Channel channel)
    {
        if (channel != null) {
            channel.close();
        }
    }

    private static class CallbackConnectionListener
            implements ChannelFutureListener
    {
        private final InetSocketAddress remoteAddress;
        private final ConnectionCallback connectionCallback;
        private final ChannelGroup openChannels;

        private CallbackConnectionListener(InetSocketAddress remoteAddress, ConnectionCallback connectionCallback, ChannelGroup openChannels)
        {
            this.remoteAddress = remoteAddress;
            this.connectionCallback = connectionCallback;
            this.openChannels = openChannels;
        }

        @Override
        public void operationComplete(ChannelFuture future)
                throws Exception
        {
            if (future.isSuccess()) {
                Channel channel = future.getChannel();
                try {
                    openChannels.add(channel);

                    connectionCallback.run(channel);
                }
                catch (Exception e) {
                    try {
                        channel.close();
                    }
                    finally {
                        connectionCallback.onError(e);
                    }
                }
            }
            else {
                Throwable cause = future.getCause();
                boolean printCause = cause != null && cause.getMessage() != null;
                SocketTimeoutException e = new SocketTimeoutException(printCause ? cause.getMessage() + " to " + remoteAddress : String.valueOf(remoteAddress));
                if (cause != null) {
                    e.initCause(cause);
                }
                connectionCallback.onError(e);
            }
        }
    }

    public static interface ConnectionCallback
    {
        void run(Channel channel)
                throws Exception;

        void onError(Throwable throwable);
    }
}
