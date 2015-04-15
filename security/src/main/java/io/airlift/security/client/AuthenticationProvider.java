package io.airlift.security.client;

import io.airlift.security.exception.AuthenticationException;
import org.eclipse.jetty.client.api.Authentication;

import static com.google.common.base.Preconditions.checkArgument;

public class AuthenticationProvider
{
    private final ClientSecurityConfig config;

    public AuthenticationProvider(ClientSecurityConfig config)
    {
        checkArgument(config != null && config.enabled(), "clientSecurityConfig is null or not enabled.");

        switch (config.getAuthScheme()) {
            case NEGOTIATE:
                break;
            default:
                throw new AuthenticationException("Unsupported scheme " + config.getAuthScheme());
        }
        this.config = config;
    }

    public Authentication createAuthentication()
    {
        return new SpnegoAuthentication(
                config.getAuthScheme(),
                config.getKrb5Conf(),
                config.getServiceName());
    }
}
