package io.airlift.security.client;

import io.airlift.security.exception.AuthenticationException;
import org.eclipse.jetty.client.api.Authentication;

import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;

public class AuthenticationProvider
{
    private final ClientSecurityConfig clientSecurityConfig;

    public AuthenticationProvider(ClientSecurityConfig clientSecurityConfig)
    {
        checkArgument(clientSecurityConfig != null && clientSecurityConfig.enabled(), "clientSecurityConfig is null or not enabled.");

        switch (clientSecurityConfig.getAuthScheme()) {
            case NEGOTIATE:
                break;
            default:
                throw new AuthenticationException("Unsupported scheme " + clientSecurityConfig.getAuthScheme());
        }
        this.clientSecurityConfig = clientSecurityConfig;
    }

    public Authentication createAuthentication(URI serviceUri)
    {
        SpnegoAuthentication spengoAuthentication = new SpnegoAuthentication(
                clientSecurityConfig.getAuthScheme(),
                clientSecurityConfig.getKrb5Conf(),
                clientSecurityConfig.getServiceName(),
                serviceUri);
        return spengoAuthentication;
    }
}
