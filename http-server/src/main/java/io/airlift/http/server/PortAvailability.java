package io.airlift.http.server;

import java.io.IOException;
import java.net.ServerSocket;

public class PortAvailability
{
    private PortAvailability() {}

    public static void checkPortAvailability(int port)
    {
        if (port == 0) {
            // Port 0 is a special case that means "any available port"
            return;
        }

        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Port " + port + " is not available", e);
        }
    }
}
