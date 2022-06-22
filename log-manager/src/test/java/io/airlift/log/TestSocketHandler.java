package io.airlift.log;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.net.HostAndPort;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.airlift.log.Format.TEXT;
import static io.airlift.log.SocketMessageOutput.createSocketHandler;
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

    @Test(dependsOnMethods = "testSocketLoggingRetries")
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

        assertEquals(handler.getRetryMessages(), 1);
    }

    @Test
    public void testSocketLoggingRetries()
            throws IOException, InterruptedException
    {
        final List<String> data = new ArrayList<String>();
        final List<String> receivedMessages = new ArrayList<String>();

        for (int i = 0; i < 1000; i++) {
            data.add(UUID.randomUUID().toString());
        }

        BufferedHandler handler = createSocketHandler(HostAndPort.fromParts("localhost", 5170),
                TEXT.createFormatter(ImmutableMap.of()), new ErrorManager());
        for (int i = 0; i < data.size() / 2; i++) {
            handler.publish(new LogRecord(Level.WARNING, data.get(i)));
        }

        Thread.sleep(1000);

        try (ServerSocket serverSocket = new ServerSocket(5170)) {
            Executors.newSingleThreadExecutor().submit(() -> {
                for (int i = data.size() / 2; i < data.size(); i++) {
                    handler.publish(new LogRecord(Level.WARNING, data.get(i)));
                }
                handler.flush();
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                handler.close();
            });
            try (Socket listeningSocket = serverSocket.accept();
                    InputStream inputStream = new BufferedInputStream(listeningSocket.getInputStream())) {
                Thread.sleep(2000);
                String received = new String(ByteStreams.toByteArray(inputStream), StandardCharsets.UTF_8);
                String[] lines = received.split("\n");
                for (String line : lines) {
                    if (line.length() != 0) {
                        receivedMessages.add(line.split("\t")[4]);
                    }
                }
            }
        }
        assertTrue(receivedMessages.containsAll(data));
    }
}
