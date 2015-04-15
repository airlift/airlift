package io.airlift.security.client;

import io.airlift.security.AuthScheme;
import org.eclipse.jetty.client.api.Authentication;

import static java.util.Objects.requireNonNull;

public abstract class JettyAuthentication
        implements Authentication
{
    private final AuthScheme scheme;

    public JettyAuthentication(AuthScheme scheme)
    {
        this.scheme = requireNonNull(scheme, "scheme is null");
    }

    public AuthScheme getAuthScheme()
    {
        return scheme;
    }
}
