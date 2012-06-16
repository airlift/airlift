package io.airlift.discovery.client;

import com.google.common.base.Preconditions;

import java.net.URI;

public class StaticAnnouncementHttpServerInfoImpl implements AnnouncementHttpServerInfo
{
    private final URI httpUri;
    private final URI httpExternalUri;

    private final URI httpsUri;
    private final URI httpsExternalUri;

    public StaticAnnouncementHttpServerInfoImpl(URI httpUri, URI httpExternalUri, URI httpsUri, URI httpsExternalUri)
    {
        Preconditions.checkArgument(
                (httpUri == null && httpExternalUri == null) ||
                (httpUri != null && httpExternalUri != null),
                "httpUri and httpExternalUri must both be null or both non-null");
        Preconditions.checkArgument(
                (httpsUri == null && httpsExternalUri == null) ||
                (httpsUri != null && httpsExternalUri != null),
                "httpsUri and httpsExternalUri must both be null or both non-null");

        this.httpUri = httpUri;
        this.httpExternalUri = httpExternalUri;
        this.httpsUri = httpsUri;
        this.httpsExternalUri = httpsExternalUri;
    }

    @Override
    public URI getHttpUri()
    {
        return httpUri;
    }

    @Override
    public URI getHttpExternalUri()
    {
        return httpExternalUri;
    }

    @Override
    public URI getHttpsUri()
    {
        return httpsUri;
    }

    @Override
    public URI getHttpsExternalUri()
    {
        return httpsExternalUri;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("StaticAnnouncementHttpServerInfoImpl");
        sb.append("{httpUri=").append(httpUri);
        sb.append(", httpExternalUri=").append(httpExternalUri);
        sb.append(", httpsUri=").append(httpsUri);
        sb.append(", httpsExternalUri=").append(httpsExternalUri);
        sb.append('}');
        return sb.toString();
    }
}
