package com.proofpoint.http.server;


import com.google.inject.Inject;
import com.google.inject.Provider;

import java.net.URI;

public class HttpServerInfo
{
    private final Provider<HttpServer> httpServerProvider;

    @Inject
    public HttpServerInfo(Provider<HttpServer> httpServerProvider)
    {
        this.httpServerProvider = httpServerProvider;
    }

    public URI getHttpUri()
    {
        return httpServerProvider.get().getHttpUri();
    }

    public URI getHttpsUri()
    {
        return httpServerProvider.get().getHttpsUri();
    }
}
