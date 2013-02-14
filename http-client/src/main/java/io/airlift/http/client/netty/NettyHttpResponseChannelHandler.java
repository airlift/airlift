package io.airlift.http.client.netty;

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
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent event)
            throws Exception
    {
        Channel channel = ctx.getChannel();
        NettyResponseFuture<?, ?> nettyResponseFuture = (NettyResponseFuture<?, ?>) ctx.getAttachment();

        HttpResponse response;
        try {
            response = (HttpResponse) event.getMessage();


            if (nettyResponseFuture != null) {
                // notify the caller
                nettyResponseFuture.completed(response);
            }
        }
        catch (Exception e) {
            // this should never happen, but be safe
            nettyResponseFuture.setException(e);
            nettyConnectionPool.destroyConnection(channel);
            return;
        } finally {
            // release http response future
            ctx.setAttachment(null);
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
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent event)
            throws Exception
    {
        handleException(ctx, event.getCause());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        handleException(ctx, new ClosedChannelException());
    }

    private void handleException(ChannelHandlerContext ctx, Throwable cause)
    {
        try {
            NettyResponseFuture<?, ?> nettyResponseFuture = (NettyResponseFuture<?, ?>) ctx.getAttachment();
            ctx.setAttachment(null);

            if (nettyResponseFuture != null) {
                if (cause instanceof ReadTimeoutException) {
                    cause = new SocketTimeoutException("Read timeout");
                }
                nettyResponseFuture.setException(cause);
            }
        }
        finally {
            // release http response future
            ctx.setAttachment(null);

            nettyConnectionPool.destroyConnection(ctx.getChannel());
        }
    }
}
