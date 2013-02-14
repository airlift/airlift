package com.proofpoint.http.client.netty;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.timeout.ReadTimeoutException;

import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;

/**
 * Final handler in Netty HTTP invocation chain.  This class converts the Netty
 * http message into an Airlift http response object, and notifies the
 * HttpResponseHandler.
 */
public class NettyHttpResponseChannelHandler
        extends SimpleChannelUpstreamHandler
{
    private final NettyConnectionPool nettyConnectionPool;

    public NettyHttpResponseChannelHandler(NettyConnectionPool nettyConnectionPool)
    {
        this.nettyConnectionPool = nettyConnectionPool;
    }

    @Override
    public void messageReceived(ChannelHandlerContext context, MessageEvent event)
            throws Exception
    {
        Channel channel = context.getChannel();
        NettyResponseFuture<?, ?> nettyResponseFuture = (NettyResponseFuture<?, ?>) context.getAttachment();

        HttpResponse response;
        try {
            response = (HttpResponse) event.getMessage();

            // this should not happen, but there may be race conditions that cause the context to be cleared
            if (nettyResponseFuture != null) {
                // notify the caller
                nettyResponseFuture.completed(response);
            }
        }
        catch (Exception e) {
            // this should never happen, but be safe
            handleException(context, e);
            return;
        } finally {
            // release http response future
            context.setAttachment(null);
        }

        // If the server requested the connection be closed, close the connection
        // Otherwise, return the connection to the pool
        String connectionHeader = response.getHeader(Names.CONNECTION);
        if (connectionHeader != null && connectionHeader.equalsIgnoreCase(Values.CLOSE)) {
            nettyConnectionPool.destroyConnection(channel);
        } else {
            nettyConnectionPool.returnConnection(channel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, ExceptionEvent event)
            throws Exception
    {
        handleException(context, event.getCause());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext context, ChannelStateEvent event)
            throws Exception
    {
        handleException(context, new ClosedChannelException());
    }

    private void handleException(ChannelHandlerContext context, Throwable cause)
    {
        try {
            NettyResponseFuture<?, ?> nettyResponseFuture = (NettyResponseFuture<?, ?>) context.getAttachment();

            if (nettyResponseFuture != null) {
                if (cause instanceof ReadTimeoutException) {
                    SocketTimeoutException socketTimeoutException = new SocketTimeoutException("Read timeout");
                    socketTimeoutException.initCause(cause);
                    cause = socketTimeoutException;
                }
                nettyResponseFuture.setException(cause);
            }
        }
        finally {
            // release http response future
            context.setAttachment(null);

            nettyConnectionPool.destroyConnection(context.getChannel());
        }
    }
}
