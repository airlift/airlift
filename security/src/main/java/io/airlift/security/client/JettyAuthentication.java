package io.airlift.security.client;

import io.airlift.security.AuthScheme;
import org.eclipse.jetty.client.api.Authentication;

import java.net.URI;

import static java.util.Objects.requireNonNull;

public abstract class JettyAuthentication
        implements Authentication
{
    private final AuthScheme scheme;
    private final URI serviceUri;

    public JettyAuthentication(AuthScheme scheme, URI serviceUri)
    {
        this.scheme = requireNonNull(scheme, "scheme is null");
        this.serviceUri = requireNonNull(serviceUri, "serviceUri is null");
    }

    public URI getServiceUri()
    {
        return serviceUri;
    }

    public AuthScheme getAuthScheme()
    {
        return scheme;
    }
}
