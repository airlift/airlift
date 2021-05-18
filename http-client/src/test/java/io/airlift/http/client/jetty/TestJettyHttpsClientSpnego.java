package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.spnego.KerberosConfig;
import org.testng.annotations.Test;

import java.io.File;
import java.io.UncheckedIOException;

import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static io.airlift.http.client.HttpStatus.UNAUTHORIZED;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.jetty.http.HttpHeader.NEGOTIATE;

public class TestJettyHttpsClientSpnego
        extends TestJettyHttpsClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setAuthenticationEnabled(true)
                .setKerberosPrincipal("invalid-for-testing")
                .setKerberosRemoteServiceName("test");
    }

    @Override
    protected KerberosConfig createKerberosConfig()
    {
        return super.createKerberosConfig()
                .setConfig(new File("/etc/krb5.conf"));
    }

    // TLS connections seem to have some conditions that do not respect timeouts
    @Test(invocationCount = 10, successPercentage = 50, timeOut = 20_000)
    @Override
    public void testConnectTimeout()
            throws Exception
    {
        super.testConnectTimeout();
    }

    @Test
    public void testNegotiateAuthScheme()
    {
        servlet.addResponseHeader(WWW_AUTHENTICATE, NEGOTIATE.asString());
        servlet.setResponseStatusCode(UNAUTHORIZED.code());

        Request request = prepareGet().setUri(baseURI).build();

        assertThatThrownBy(() -> executeRequest(request, createStringResponseHandler()))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageStartingWith("Failed reading response from server: ")
                .satisfies(e -> assertThat(e.getCause().getMessage())
                        .contains(" Failed to establish LoginContext for request "));
    }
}
