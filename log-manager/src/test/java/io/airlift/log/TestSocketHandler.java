package io.airlift.log;

import com.google.common.io.ByteStreams;
import com.google.common.net.HostAndPort;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.airlift.log.Format.TEXT;
import static io.airlift.log.SocketMessageOutput.createSocketHandler;
import static org.testng.Assert.assertEquals;

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
                BufferedHandler handler = createSocketHandler(HostAndPort.fromParts("localhost", serverSocket.getLocalPort()), TEXT.getFormatter());
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

        BufferedHandler handler = createSocketHandler(HostAndPort.fromParts("localhost", unallocatedPort), TEXT.getFormatter());
        handler.publish(new LogRecord(Level.SEVERE, "rutabaga"));
        handler.flush();
        handler.close();

        assertEquals(((SocketMessageOutput) handler.getMessageOutput()).getFailedConnections(), 5);
    }
}
