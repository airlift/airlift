package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static io.airlift.concurrent.Threads.threadsNamed;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class TestingSocksProxy
        implements Closeable
{
    private static final int SOCKS_4_SUCCESS = 0x5a;
    private static final int SOCKS_4_FAILED = 0x5b;

    private static final int SOCKS_5_ADDRESS_V4 = 0x01;
    private static final int SOCKS_5_ADDRESS_DOMAIN = 0x03;
    private static final int SOCKS_5_ADDRESS_V6 = 0x04;

    private static final int SOCKS_5_STATUS_SUCCESS = 0x00;
    private static final int SOCKS_5_STATUS_FAILED = 0x01;

    private final int bindPort;

    private HostAndPort hostAndPort;
    private ListeningExecutorService executorService;
    private ServerSocket serverSocket;

    public TestingSocksProxy()
    {
        this(0);
    }

    public TestingSocksProxy(int bindPort)
    {
        this.bindPort = bindPort;
    }

    public synchronized HostAndPort getHostAndPort()
    {
        checkState(hostAndPort != null, "%s is not running", getClass().getName());
        return hostAndPort;
    }

    public synchronized TestingSocksProxy start()
            throws IOException
    {
        checkState(serverSocket == null, "%s already started", getClass().getName());

        try {
            serverSocket = new ServerSocket(bindPort, 50, InetAddress.getByName("127.0.0.1"));
            hostAndPort = HostAndPort.fromParts(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort());
            executorService = listeningDecorator(newCachedThreadPool(threadsNamed("socks-proxy-" + serverSocket.getLocalPort() + "-%s")));

            executorService.execute(new SocksProxyAcceptor(serverSocket, executorService));

            return this;
        }
        catch (Throwable e) {
            close();
            throw e;
        }
    }

    @Override
    public synchronized void close()
    {
        hostAndPort = null;
        if (serverSocket != null) {
            closeIgnoreException(serverSocket);
            serverSocket = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    private static class SocksProxyAcceptor
            implements Runnable
    {
        private final ServerSocket serverSocket;
        private final ListeningExecutorService executorService;
        private final AtomicBoolean closed = new AtomicBoolean();

        private SocksProxyAcceptor(ServerSocket serverSocket, ListeningExecutorService executorService)
        {
            this.serverSocket = serverSocket;
            this.executorService = executorService;
        }

        @Override
        public void run()
        {
            while (!closed.get() && !serverSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket socket = serverSocket.accept();
                    executorService.execute(new SocksProxyWorker(socket, executorService));
                }
                catch (IOException ignored) {
                    // doesn't really matter
                }
            }
            closeIgnoreException(serverSocket);
        }
    }

    private static class SocksProxyWorker
            implements Runnable
    {
        private final Socket socket;
        private final ListeningExecutorService executor;

        private SocksProxyWorker(Socket socket, ListeningExecutorService executor)
        {
            this.socket = socket;
            this.executor = executor;
        }

        @Override
        public void run()
        {
            try {
                connect();
            }
            catch (IOException e) {
                // ignored nothing we can do about this
                closeIgnoreException(socket);
            }
            catch (Throwable e) {
                closeIgnoreException(socket);
                throw e;
            }
        }

        private void connect()
                throws IOException
        {
            DataInputStream sourceInput = new DataInputStream(socket.getInputStream());
            DataOutputStream sourceOutput = new DataOutputStream(socket.getOutputStream());

            // field 1: SOCKS version number, 1 byte
            int version = sourceInput.read();
            if (version == 4) {
                socks4(sourceInput, sourceOutput);
            }
            else if (version == 5) {
                socks5(sourceInput, sourceOutput);
            }

            // unsupported version, just close the socket
        }

        private void socks4(DataInputStream sourceInput, DataOutputStream sourceOutput)
                throws IOException
        {
            // field 2: command code, 1 byte: 0x01 = connect, 0x02 = bind
            int command = sourceInput.read();

            // field 3: network byte order port number, 2 bytes
            int port = sourceInput.readUnsignedShort();

            // field 4: network byte order IP address, 4 bytes
            int address = sourceInput.readInt();

            // field 5: the user ID string, variable length, terminated with a null (0x00)
            while (sourceInput.read() != 0) {
                // ignored
            }

            if (command != 1) {
                // we only support connect requests
                responseSocks4(sourceOutput, SOCKS_4_FAILED, 0, 0);
                return;
            }

            // Socks 4a: if address is 0x0000_00xx where xx is not 0, we have a domain name
            String domainName = null;
            if (address != 0 && (address & 0xFFFF_FF00) == 0) {
                // field 6: the domain name of the host we want to contact, variable length, terminated with a null (0x00)
                StringBuilder domainNameBuilder = new StringBuilder(64);
                for (int value = sourceInput.read(); value != 0; value = sourceInput.read()) {
                    domainNameBuilder.append((char) value);
                }
                domainName = domainNameBuilder.toString();
            }

            Socket targetSocket;
            try {
                if (domainName != null) {
                    targetSocket = new Socket(domainName, port);
                }
                else {
                    targetSocket = new Socket(InetAddresses.fromInteger(address), port);
                }
            }
            catch (IOException e) {
                // could not resolve name or open socket
                responseSocks4(sourceOutput, SOCKS_4_FAILED, 0, 0);
                return;
            }

            InputStream targetInput = targetSocket.getInputStream();
            OutputStream targetOutput = targetSocket.getOutputStream();

            // send success message
            responseSocks4(sourceOutput, SOCKS_4_SUCCESS, port, InetAddresses.coerceToInteger(targetSocket.getInetAddress()));
            proxyData(sourceInput, sourceOutput, targetInput, targetOutput);
        }

        private static void responseSocks4(DataOutputStream output, int status, int port, int address)
                throws IOException
        {
            ByteArrayDataOutput sourceOutput = ByteStreams.newDataOutput();

            // field 1: null byte
            sourceOutput.write(0);

            // field 2: status, 1 byte:
            sourceOutput.write(status);

            // field 3: network byte order port number, 2 bytes
            sourceOutput.writeShort(port);

            // field 4: network byte order IP address, 4 bytes
            sourceOutput.writeInt(address);

            // write all at once to avoid Jetty bug
            // TODO: remove this when fixed in Jetty
            output.write(sourceOutput.toByteArray());
        }

        private void socks5(DataInputStream sourceInput, DataOutputStream sourceOutput)
                throws IOException
        {
            // field 2: number of authentication methods supported, 1 byte
            int authMethods = sourceInput.read();

            // field 3: authentication methods, variable length, 1 byte per method supported
            boolean supportsNoAuth = false;
            for (int i = 0; i < authMethods; i++) {
                // read auth methods
                if (sourceInput.read() == 0) {
                    supportsNoAuth = true;
                }
            }

            if (!supportsNoAuth) {
                // no supported auth method
                sourceOutput.write(5);
                sourceOutput.write(0xFF);
                return;
            }

            sourceOutput.write(5);
            sourceOutput.write(0);

            // field 1: SOCKS version number, 1 byte (must be 0x05 for this version)
            int version = sourceInput.read();
            if (version != 5) {
                return;
            }

            // field 2: command code, 1 byte:
            int command = sourceInput.read();

            // field 3: reserved, must be 0x00
            sourceInput.read();

            // field 4: address type, 1 byte:
            int addressType = sourceInput.read();

            // field 5: destination address of
            byte[] address;
            switch (addressType) {
                case SOCKS_5_ADDRESS_V4:
                    // 4 bytes for IPv4 address
                    address = new byte[4];
                    sourceInput.readFully(address);
                    break;
                case SOCKS_5_ADDRESS_DOMAIN:
                    // 1 byte of name length followed by the name for Domain name
                    address = new byte[sourceInput.read()];
                    sourceInput.readFully(address);
                    break;
                case SOCKS_5_ADDRESS_V6:
                    // 16 bytes for IPv6 address
                    address = new byte[16];
                    sourceInput.readFully(address);
                    break;
                default:
                    // unknown address type, terminate connection
                    return;
            }

            // field 6: port number in a network byte order, 2 bytes
            int port = sourceInput.readUnsignedShort();

            // we only support connect requests
            if (command != 1) {
                responseSocks5(sourceOutput, SOCKS_5_STATUS_FAILED, port, addressType, address);
                return;
            }

            Socket targetSocket;
            try {
                switch (addressType) {
                    case SOCKS_5_ADDRESS_V4:
                    case SOCKS_5_ADDRESS_V6:
                        targetSocket = new Socket(InetAddress.getByAddress(address), port);
                        break;
                    case SOCKS_5_ADDRESS_DOMAIN:
                        targetSocket = new Socket(new String(address, StandardCharsets.US_ASCII), port);
                        break;
                    default:
                        return;
                }
            }
            catch (IOException e) {
                // could not resolve name or open socket
                responseSocks5(sourceOutput, SOCKS_5_STATUS_FAILED, port, addressType, address);
                return;
            }

            InputStream targetInput = targetSocket.getInputStream();
            OutputStream targetOutput = targetSocket.getOutputStream();

            // send success message
            responseSocks5(sourceOutput, SOCKS_5_STATUS_SUCCESS, port, addressType, address);
            proxyData(sourceInput, sourceOutput, targetInput, targetOutput);
        }

        private static void responseSocks5(DataOutputStream output, int status, int port, int addressType, byte[] address)
                throws IOException
        {
            ByteArrayDataOutput sourceOutput = ByteStreams.newDataOutput();

            // field 1: SOCKS protocol version, 1 byte (0x05 for this version)
            sourceOutput.write(5);

            // field 2: status, 1 byte:
            sourceOutput.write(status);

            // field 3: reserved, must be 0x00
            sourceOutput.write(0);

            // field 4: address type, 1 byte
            sourceOutput.write(addressType);

            // field 5: destination address
            sourceOutput.write(address);

            // field 6: network byte order port number, 2 bytes
            sourceOutput.writeShort(port);

            // write all at once to avoid Jetty bug
            // TODO: remove this when fixed in Jetty
            output.write(sourceOutput.toByteArray());
        }

        private void proxyData(InputStream sourceInput, OutputStream sourceOutput, InputStream targetInput, OutputStream targetOutput)
        {
            // pipe in to out and out to in
            List<ListenableFuture<?>> jobs = ImmutableList.of(
                    executor.submit(new Pipe(sourceInput, targetOutput)),
                    executor.submit(new Pipe(targetInput, sourceOutput)));

            // close socket when both jobs finish
            Futures.addCallback(Futures.allAsList(jobs), new FutureCallback<List<Object>>()
            {
                @Override
                public void onSuccess(List<Object> result)
                {
                    closeIgnoreException(socket);
                }

                @Override
                public void onFailure(Throwable ignored)
                {
                    closeIgnoreException(socket);
                }
            });
        }
    }

    private static class Pipe
            implements Runnable
    {
        private final InputStream in;
        private final OutputStream out;

        private Pipe(InputStream in, OutputStream out)
        {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run()
        {
            try {
                ByteStreams.copy(in, out);
            }
            catch (IOException e) {
                // ignored nothing we can do about this
            }
            finally {
                closeIgnoreException(in);
                closeIgnoreException(out);
            }
        }
    }

    private static void closeIgnoreException(Closeable closeable)
    {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        }
        catch (IOException ignored) {
            // nothing we can do about this
        }
    }
}
