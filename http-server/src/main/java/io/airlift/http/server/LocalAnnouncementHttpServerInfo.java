package com.proofpoint.http.server;

import com.proofpoint.discovery.client.AnnouncementHttpServerInfo;

import javax.inject.Inject;
import java.net.URI;

public class LocalAnnouncementHttpServerInfo implements AnnouncementHttpServerInfo
{
    private final HttpServerInfo httpServerInfo;

    @Inject
    public LocalAnnouncementHttpServerInfo(HttpServerInfo httpServerInfo)
    {
        this.httpServerInfo = httpServerInfo;
    }

    @Override
    public URI getHttpUri()
    {
        return httpServerInfo.getHttpUri();
    }

    @Override
    public URI getHttpExternalUri()
    {
        return httpServerInfo.getHttpExternalUri();
    }

    @Override
    public URI getHttpsUri()
    {
        return httpServerInfo.getHttpsUri();
    }

    @Override
    public URI getHttpsExternalUri()
    {
        return httpServerInfo.getHttpsExternalUri();
    }
}
