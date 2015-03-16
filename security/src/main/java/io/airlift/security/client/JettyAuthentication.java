package io.airlift.security.client;

import io.airlift.security.AuthScheme;
import org.eclipse.jetty.client.api.Authentication;

import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class JettyAuthentication implements Authentication
{
    private final AuthScheme authScheme;
    private final URI serviceUri;

    public JettyAuthentication(AuthScheme authScheme, URI serviceUri)
    {
        checkNotNull(authScheme, "authScheme is null");
        checkNotNull(serviceUri, "serviceUri is null");
        this.authScheme = authScheme;
        this.serviceUri = serviceUri;
    }

    public URI getServiceUri()
    {
        return serviceUri;
    }

    public AuthScheme getAuthScheme()
    {
        return authScheme;
    }
}
