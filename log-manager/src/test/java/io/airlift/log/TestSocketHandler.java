package io.airlift.log;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import com.google.common.io.ByteStreams;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.RateLimiter;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.airlift.log.Format.TEXT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test(timeOut = 5 * 60 * 1000)
public class TestSocketHandler
{
    @Test
    public void testSocketLogging()
            throws IOException
    {
        final String[] data = {"apple", "banana", "orange"};
        final Level[] levels = {Level.SEVERE, Level.INFO, Level.WARNING};

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Executors.newSingleThreadExecutor().submit(() -> {
                BufferedHandler handler = createSocketHandler(HostAndPort.fromParts("localhost", serverSocket.getLocalPort()), TEXT.createFormatter(ImmutableMap.of()), new ErrorManager());
                for (int i = 0; i < data.length; i++) {
                    handler.publish(new LogRecord(levels[i], data[i]));
                }
                handler.flush();
                handler.close();
            });

            try (Socket listeningSocket = serverSocket.accept();
                    InputStream inputStream = listeningSocket.getInputStream()) {
                String received = new String(ByteStreams.toByteArray(inputStream), StandardCharsets.UTF_8);
                String[] lines = received.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String[] parts = lines[i].split("\t");
                    assertEquals(parts[1], io.airlift.log.Level.fromJulLevel(levels[i]).toString());
                    assertEquals(parts[4], data[i]);
                }
            }
        }
    }

    @Test
    public void testBadListeningSocket()
            throws IOException
    {
        ServerSocket serverSocket = new ServerSocket(0);
        int unallocatedPort = serverSocket.getLocalPort();
        serverSocket.close();

        BufferedHandler handler = createSocketHandler(HostAndPort.fromParts("localhost", unallocatedPort), TEXT.createFormatter(ImmutableMap.of()), new ErrorManager());
        handler.publish(new LogRecord(Level.SEVERE, "rutabaga"));
        handler.flush();
        handler.close();

        assertTrue(((SocketMessageOutput) handler.getMessageOutput()).getFailedConnections() > 0);
    }

    private static BufferedHandler createSocketHandler(HostAndPort hostAndPort, Formatter formatter, ErrorManager errorManager)
    {
        SocketMessageOutput output = new SocketMessageOutput(hostAndPort);
        BufferedHandler handler = new BufferedHandler(output, formatter, Multiset::toString, errorManager, RateLimiter.create(10), Duration.ofMillis(100), 512, 1024);
        handler.initialize();
        return handler;
    }
}
