package io.airlift.log;

import com.google.common.net.HostAndPort;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;

import javax.annotation.concurrent.GuardedBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;

import static java.util.Objects.requireNonNull;

public class SocketMessageOutput
        implements MessageOutput
{
    private static final Logger log = Logger.get(SocketMessageOutput.class);
    private static final int CONNECTION_TIMEOUT_MILLIS = 100;
    private static final int MAX_WRITE_ATTEMPTS_PER_MESSAGE = 5;

    private final InetSocketAddress socketAddress;
    private final AtomicLong failedConnections = new AtomicLong(0);
    private final ExponentialBackOff errorBackOff = new ExponentialBackOff(
            new Duration(100, TimeUnit.MILLISECONDS),
            new Duration(60, TimeUnit.SECONDS),
                    "Log TCP listener is up",
                    "Log TCP listener is down",
                    log);

    @GuardedBy("this")
    private Socket socket;
    @GuardedBy("this")
    private OutputStream currentOutputStream;

    private SocketMessageOutput(HostAndPort hostAndPort)
    {
        requireNonNull(hostAndPort, "hostAndPort is null");
        this.socketAddress = new InetSocketAddress(hostAndPort.getHost(), hostAndPort.getPort());
    }

    public static BufferedHandler createSocketHandler(HostAndPort hostAndPort, Formatter formatter, ErrorManager errorManager)
    {
        SocketMessageOutput output = new SocketMessageOutput(hostAndPort);
        BufferedHandler handler = new BufferedHandler(output, formatter, errorManager);
        handler.start();
        return handler;
    }

    @Override
    public synchronized void writeMessage(byte[] message)
            throws IOException
    {
        IOException lastException = null;
        int connectionFailures = 0;
        AtomicLong initialDelay;
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
                    initialDelay = new AtomicLong(errorBackOff.failed().toMillis());
                    try {
                        Thread.sleep(initialDelay.get());
                    }
                    catch (InterruptedException ignored) {
                    }
                    continue;
                }
            }

            try {
                currentOutputStream.write(message);
                errorBackOff.success();
                break;
            }
            catch (IOException e) {
                socket.close();
                socket = null;
                currentOutputStream = null;
                connectionFailures++;
                initialDelay = new AtomicLong(errorBackOff.failed().toMillis());
                try {
                    Thread.sleep(initialDelay.get());
                }
                catch (InterruptedException ignored) {
                }
                lastException = e;
            }
        }

        if (connectionFailures > 0) {
            errorBackOff.reset();
            failedConnections.addAndGet(connectionFailures);
            throw new IOException("Exception caught connecting via socket to " + socketAddress.getHostName()
                    + " on port " + socketAddress.getPort() + ". "
                    + "There were " + connectionFailures + " failures attempting to write the log message. "
                    + (connectionFailures == MAX_WRITE_ATTEMPTS_PER_MESSAGE ? "The log message was likely dropped." : "The log message was sent."), lastException);
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
