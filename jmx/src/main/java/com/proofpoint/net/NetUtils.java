package com.proofpoint.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class NetUtils
{
    public static int findUnusedPort()
            throws IOException
    {
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(0));
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }
}
