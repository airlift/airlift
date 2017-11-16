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
import static org.eclipse.jetty.http.HttpHeader.NEGOTIATE;

public class TestJettyHttpsClientSpengo
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

    @Test(expectedExceptions = UncheckedIOException.class, expectedExceptionsMessageRegExp = ".* Failed to establish LoginContext for request .*")
    public void testNegotiateAuthScheme()
            throws Exception
    {
        servlet.addResponseHeader(WWW_AUTHENTICATE, NEGOTIATE.asString());
        servlet.setResponseStatusCode(UNAUTHORIZED.code());

        Request request = prepareGet().setUri(baseURI).build();

        executeRequest(request, createStringResponseHandler());
    }
}
