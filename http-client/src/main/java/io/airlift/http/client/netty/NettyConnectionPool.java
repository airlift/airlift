package io.airlift.http.client.netty;

import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WARNING: Actual pooling is not tested yet.
 */
@ThreadSafe
public class NettyConnectionPool
{
    private final ChannelGroup openChannels = new DefaultChannelGroup("http-client");
    private final ClientBootstrap bootstrap;

    private final Executor executor;
    private final PermitQueue connectionPermits;

    @GuardedBy("this")
    private final LinkedListMultimap<HostAndPort, Channel> channelCache = LinkedListMultimap.create();

    private final int maxConnections;
    private final AtomicInteger checkedOutConnections = new AtomicInteger();
    private final boolean enablePooling;

    public NettyConnectionPool(ClientBootstrap bootstrap,
            int maxConnections,
            Executor executorService,
            boolean enablePooling)
    {
        this.bootstrap = bootstrap;
        this.maxConnections = maxConnections;
        this.connectionPermits = new PermitQueue(this.maxConnections);
        this.executor = executorService;
        this.enablePooling = enablePooling;
    }

    public void close()
    {
        try {
            openChannels.close();
        }
        finally {
            bootstrap.releaseExternalResources();
        }
    }

    public void execute(URI uri, final ConnectionCallback connectionCallback)
    {
        int port = uri.getPort();
        if (port < 0) {
            // we do not support https
            port = 80;
        }
        final InetSocketAddress remoteAddress = new InetSocketAddress(uri.getHost(), port);

        if (enablePooling) {
            ListenableFuture<?> acquireFuture = connectionPermits.acquire();
            acquireFuture.addListener(new Runnable()
            {
                @Override
                public void run()
                {
                    connectionPermitAcquired(remoteAddress, connectionCallback);
                }
            }, executor);
        }
        else {
            ChannelFuture future = bootstrap.connect(remoteAddress);
            future.addListener(new CallbackConnectionListener(remoteAddress, connectionCallback, openChannels));
        }
    }

    private void connectionPermitAcquired(InetSocketAddress remoteAddress, ConnectionCallback connectionCallback)
    {
        Preconditions.checkState(enablePooling, "Pooling is not enabled");
        Channel channel = null;
        synchronized (this) {
            HostAndPort key = toHostAndPort(remoteAddress);

            // find an existing connected channel
            List<Channel> channels = channelCache.get(key);
            while (channel == null && !channels.isEmpty()) {
                // remove last
                channel = channels.remove(channels.size() - 1);

                if (!channel.isConnected()) {
                    channel.close();
                    channel = null;
                }
            }

            if (channel == null) {
                // we did not find an existing connection, so we will create a new connection
                // if there are already too many pooled connection, destroy some

                int pooledConnectionCount = channelCache.size();
                int checkedOutConnectionCount = this.checkedOutConnections.get();

                int connectionsToDestroy = (checkedOutConnectionCount + pooledConnectionCount + 1) - maxConnections;
                for (int i = 0; !channels.isEmpty() && i < connectionsToDestroy; i++) {
                    Channel victim = channels.remove(channels.size() - 1);
                    victim.close();
                }
            }
        }

        checkedOutConnections.incrementAndGet();
        if (channel == null) {
            // we have permission to own a connection, but no exiting connection was found
            ChannelFuture future = bootstrap.connect(remoteAddress);
            future.addListener(new CallbackConnectionListener(remoteAddress, connectionCallback, openChannels));
        }
        else {
            try {
                // we are in a user worker thread so it is ok to invoke the callback
                connectionCallback.run(channel);
            }
            catch (Throwable e) {
                connectionCallback.onError(e);
            }
        }
    }

    public synchronized void returnConnection(Channel channel)
    {
        try {
            if (channel != null) {
                // if pooling return the connection
                if (enablePooling && channel.isConnected()) {
                    InetSocketAddress remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
                    // remote address should never be null for a connected socket, but be safe
                    if (remoteAddress != null) {
                        HostAndPort key = toHostAndPort(remoteAddress);
                        channelCache.put(key, channel);
                        return;
                    }
                }
                channel.close();
            }
        }
        finally {
            checkedOutConnections.decrementAndGet();
            if (enablePooling) {
                connectionPermits.release();
            }
        }
    }

    public synchronized void destroyConnection(Channel channel)
    {
        try {
            if (channel != null) {
                channel.close();
            }
        }
        finally {
            checkedOutConnections.decrementAndGet();
            if (enablePooling) {
                connectionPermits.release();
            }
        }
    }

    private HostAndPort toHostAndPort(InetSocketAddress remoteAddress)
    {
        String address = InetAddresses.toAddrString(remoteAddress.getAddress());
        return HostAndPort.fromParts(address, remoteAddress.getPort());
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

                    // todo add close callback handler to remove this from the cache

                    connectionCallback.run(channel);
                }
                catch (Throwable e) {
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
                String message = String.valueOf(remoteAddress);
                if (cause != null && cause.getMessage() != null) {
                   message = cause.getMessage() + " to " + remoteAddress;
                }

                SocketTimeoutException e = new SocketTimeoutException(message);
                e.initCause(cause);

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
