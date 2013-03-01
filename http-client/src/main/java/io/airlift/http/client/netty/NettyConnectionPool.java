package io.airlift.http.client.netty;

import com.google.common.base.Objects;
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
import org.jboss.netty.handler.ssl.SslHandler;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
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
    private final LinkedListMultimap<PoolKey, Channel> channelCache = LinkedListMultimap.create();

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
        final boolean isSsl = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort();
        if (port < 0) {
            if (isSsl) {
                port = 443;
            }
            else {
                port = 80;
            }
        }
        final InetSocketAddress remoteAddress = new InetSocketAddress(uri.getHost(), port);

        if (enablePooling) {
            ListenableFuture<?> acquireFuture = connectionPermits.acquire();
            acquireFuture.addListener(new Runnable()
            {
                @Override
                public void run()
                {
                    connectionPermitAcquired(isSsl, remoteAddress, connectionCallback);
                }
            }, executor);
        }
        else {
            openConnecton(isSsl, remoteAddress, connectionCallback);
        }
    }

    private void connectionPermitAcquired(boolean isSsl, InetSocketAddress remoteAddress, ConnectionCallback connectionCallback)
    {
        Preconditions.checkState(enablePooling, "Pooling is not enabled");
        Channel channel = null;
        synchronized (this) {
            PoolKey key = new PoolKey(isSsl, remoteAddress);

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
            openConnecton(isSsl, remoteAddress, connectionCallback);
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

    private void openConnecton(boolean isSsl, InetSocketAddress remoteAddress, ConnectionCallback connectionCallback)
    {
        ChannelFuture future = bootstrap.connect(remoteAddress);
        if (isSsl) {
            future.addListener(new SslConnectionListener(remoteAddress, connectionCallback, openChannels));
        }
        else {
            future.addListener(new CallbackConnectionListener(remoteAddress, connectionCallback, openChannels));
        }
    }

    public synchronized void returnConnection(Channel channel)
    {
        try {
            if (channel != null) {
                // if pooling return the connection
                if (enablePooling && channel.isConnected()) {
                    InetSocketAddress remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
                    boolean isSsl = channel.getPipeline().get(SslHandler.class) != null;
                    // remote address should never be null for a connected socket, but be safe
                    if (remoteAddress != null) {
                        PoolKey key = new PoolKey(isSsl, remoteAddress);
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

    private static class SslConnectionListener implements ChannelFutureListener
    {

        private final InetSocketAddress remoteAddress;
        private final ConnectionCallback connectionCallback;
        private final ChannelGroup openChannels;

        public SslConnectionListener(InetSocketAddress remoteAddress, ConnectionCallback connectionCallback, ChannelGroup openChannels)
        {
            this.remoteAddress = remoteAddress;
            this.connectionCallback = connectionCallback;
            this.openChannels = openChannels;
        }

        @Override
        public void operationComplete(ChannelFuture future)
                throws Exception
        {
            CallbackConnectionListener callbackConnectionListener = new CallbackConnectionListener(remoteAddress, connectionCallback, openChannels);
            if (future.isSuccess()) {
                SSLParameters sslParameters = new SSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");

                SSLEngine sslEngine = SSLContext.getDefault().createSSLEngine(remoteAddress.getHostName(), remoteAddress.getPort());
                sslEngine.setSSLParameters(sslParameters);
                sslEngine.setUseClientMode(true);

                SslHandler sslHandler = new SslHandler(sslEngine);
                future.getChannel().getPipeline().addBefore("codec", "ssl", sslHandler);
                ChannelFuture handshakeFuture = sslHandler.handshake();
                handshakeFuture.addListener(callbackConnectionListener);
            }
            else {
                callbackConnectionListener.operationComplete(future);
            }
        }
    }

    private static class PoolKey
    {
        private final boolean isSsl;
        private final HostAndPort hostAndPort;

        PoolKey(boolean isSsl, InetSocketAddress remoteAddress)
        {
            this.isSsl = isSsl;
            if (isSsl) {
                // A connection using a hostname that matches the cert shouldn't be
                // reused for another hostname that doesn't, so cannot use the IP as key.
                hostAndPort = HostAndPort.fromParts(remoteAddress.getHostName(), remoteAddress.getPort());
            }
            else {
                String address = InetAddresses.toAddrString(remoteAddress.getAddress());
                hostAndPort = HostAndPort.fromParts(address, remoteAddress.getPort());
            }
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(isSsl, hostAndPort);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final PoolKey other = (PoolKey) obj;
            return Objects.equal(this.isSsl, other.isSsl) &&
                    Objects.equal(this.hostAndPort, other.hostAndPort);
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this)
                    .add("isSsl", isSsl)
                    .add("hostAndPort", hostAndPort)
                    .toString();
        }
    }
}
