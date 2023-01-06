package io.airlift.log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import org.weakref.jmx.Managed;

import javax.annotation.concurrent.GuardedBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;

import static java.util.Objects.requireNonNull;

public class SocketMessageOutput
        implements MessageOutput
{
    private static final int CONNECTION_TIMEOUT_MILLIS = 100;
    private static final int MAX_WRITE_ATTEMPTS_PER_MESSAGE = 5;

    private final InetSocketAddress socketAddress;
    private final AtomicLong failedConnections = new AtomicLong(0);

    @GuardedBy("this")
    private Socket socket;
    @GuardedBy("this")
    private OutputStream currentOutputStream;

    @VisibleForTesting
    SocketMessageOutput(HostAndPort hostAndPort)
    {
        requireNonNull(hostAndPort, "hostAndPort is null");
        this.socketAddress = new InetSocketAddress(hostAndPort.getHost(), hostAndPort.getPort());
    }

    public static BufferedHandler createSocketHandler(HostAndPort hostAndPort, Formatter formatter, ErrorManager errorManager)
    {
        SocketMessageOutput output = new SocketMessageOutput(hostAndPort);
        BufferedHandler handler = new BufferedHandler(output, formatter, errorManager);
        handler.initialize();
        return handler;
    }

    @Override
    public synchronized void writeMessage(byte[] message)
            throws IOException
    {
        IOException lastException = null;
        boolean success = false;
        int connectionFailures = 0;
        for (int i = 0; i < MAX_WRITE_ATTEMPTS_PER_MESSAGE; i++) {
            if (socket == null || socket.isClosed() || currentOutputStream == null) {
                try {
                    socket = new Socket();
                    socket.connect(socketAddress, CONNECTION_TIMEOUT_MILLIS);
                    currentOutputStream = socket.getOutputStream();
                }
                catch (IOException e) {
                    socket.close();
                    socket = null;
                    currentOutputStream = null;
                    lastException = e;
                    connectionFailures++;
                    continue;
                }
            }

            try {
                currentOutputStream.write(message);
                success = true;
                break;
            }
            catch (IOException e) {
                socket.close();
                socket = null;
                currentOutputStream = null;
                connectionFailures++;
                lastException = e;
            }
        }

        if (connectionFailures > 0) {
            failedConnections.addAndGet(connectionFailures);
            if (!success) {
                throw new IOException(
                        "Exception caught connecting via socket to %s:%s. There were %s failures attempting to write the log message.".formatted(
                                socketAddress.getHostName(),
                                socketAddress.getPort(),
                                connectionFailures),
                        lastException);
            }
        }
    }

    @Override
    public synchronized void flush()
            throws IOException
    {
        if (currentOutputStream != null) {
            currentOutputStream.flush();
        }
    }

    @Override
    public synchronized void close()
            throws IOException
    {
        IOException exception = new IOException("Exception thrown attempting to close the output stream and socket.");

        if (currentOutputStream != null) {
            try {
                currentOutputStream.flush();
            }
            catch (IOException e) {
                exception.addSuppressed(e);
            }
            try {
                currentOutputStream.close();
            }
            catch (IOException e) {
                exception.addSuppressed(e);
            }
        }

        currentOutputStream = null;

        if (socket != null) {
            try {
                socket.close();
            }
            catch (IOException e) {
                exception.addSuppressed(e);
            }
        }

        socket = null;

        if (exception.getSuppressed().length > 0) {
            throw exception;
        }
    }

    @Managed
    public long getFailedConnections()
    {
        return failedConnections.get();
    }
}
