package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.spnego.KerberosConfig;
import io.airlift.http.client.spnego.SpnegoAuthentication;
import io.airlift.http.client.spnego.SpnegoAuthenticationStore;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;

// By wrapping HttpClient, we are able to substitute the underlying AuthenticationStore
// with a more efficient one.
class SpnegoHttpClient
        extends HttpClient
{
    private final AuthenticationStore authenticationStore;
    private final SpnegoAuthentication spnego;

    public SpnegoHttpClient(KerberosConfig kerberosConfig, HttpClientConfig config, HttpClientTransport transport, SslContextFactory sslContextFactory)
    {
        super(transport, sslContextFactory);

        spnego = new SpnegoAuthentication(
                kerberosConfig.getKeytab(),
                kerberosConfig.getConfig(),
                kerberosConfig.getCredentialCache(),
                config.getKerberosPrincipal(),
                config.getKerberosRemoteServiceName(),
                kerberosConfig.isUseCanonicalHostname());

        authenticationStore = new SpnegoAuthenticationStore(spnego);
    }

    @Override
    public AuthenticationStore getAuthenticationStore()
    {
        return authenticationStore;
    }

    @Override
    protected void doStop()
            throws Exception
    {
        authenticationStore.clearAuthenticationResults();
        super.doStop();
    }
}
