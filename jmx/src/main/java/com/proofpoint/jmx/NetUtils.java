package com.proofpoint.jmx;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

final class NetUtils
{
    public static int findUnusedPort()
            throws IOException
    {
        int port;

        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
        }
        finally {
            socket.close();
        }

        return port;
    }
}
