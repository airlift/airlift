package io.airlift.http.server;

import com.google.common.base.Throwables;
import com.google.common.net.InetAddresses;
import com.google.inject.Inject;
import io.airlift.node.NodeInfo;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;

public class HttpServerInfo
{
    private final URI httpUri;
    private final URI httpExternalUri;
    private final URI httpsUri;
    private final URI httpsExternalUri;
    private final URI adminUri;
    private final URI adminExternalUri;

    @Inject
    public HttpServerInfo(HttpServerConfig config, NodeInfo nodeInfo)
    {
        if (config.isHttpEnabled()) {
            httpUri = buildUri("http", InetAddresses.toUriString(nodeInfo.getInternalIp()), config.getHttpPort());
            httpExternalUri = buildUri("http", nodeInfo.getExternalAddress(), httpUri.getPort());
        }
        else {
            httpUri = null;
            httpExternalUri = null;
        }

        if (config.isHttpsEnabled()) {
            httpsUri = buildUri("https", InetAddresses.toUriString(nodeInfo.getInternalIp()), config.getHttpsPort());
            httpsExternalUri = buildUri("https", nodeInfo.getExternalAddress(), httpsUri.getPort());
        }
        else {
            httpsUri = null;
            httpsExternalUri = null;
        }

        if (config.isAdminEnabled()) {
            if (config.isHttpsEnabled()) {
                adminUri = buildUri("https", InetAddresses.toUriString(nodeInfo.getInternalIp()), config.getAdminPort());
                adminExternalUri = buildUri("https", nodeInfo.getExternalAddress(), adminUri.getPort());
            } else {
                adminUri = buildUri("http", InetAddresses.toUriString(nodeInfo.getInternalIp()), config.getAdminPort());
                adminExternalUri = buildUri("http", nodeInfo.getExternalAddress(), adminUri.getPort());
            }
        }
        else {
            adminUri = null;
            adminExternalUri = null;
        }
    }

    public URI getHttpUri()
    {
        return httpUri;
    }

    public URI getHttpExternalUri()
    {
        return httpExternalUri;
    }

    public URI getHttpsUri()
    {
        return httpsUri;
    }

    public URI getHttpsExternalUri()
    {
        return httpsExternalUri;
    }

    public URI getAdminUri()
    {
        return adminUri;
    }

    public URI getAdminExternalUri()
    {
        return adminExternalUri;
    }

    private static URI buildUri(String scheme, String host, int port)
    {
        try {
            // 0 means select a random port
            if (port == 0) {
                ServerSocket socket = new ServerSocket();
                try {
                    socket.bind(new InetSocketAddress(0));
                    port = socket.getLocalPort();
                }
                finally {
                    socket.close();
                }
            }

            return new URI(scheme, null, host, port, null, null, null);
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
