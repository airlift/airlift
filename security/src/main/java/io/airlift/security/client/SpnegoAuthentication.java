package io.airlift.security.client;

import io.airlift.log.Logger;
import io.airlift.security.AuthScheme;
import io.airlift.security.exception.AuthenticationException;
import io.airlift.security.utils.KerberosUtil;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.B64Code;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import static java.util.Objects.requireNonNull;

public class SpnegoAuthentication
        extends JettyAuthentication
{
    private static final String NEGOTIATE = HttpHeader.NEGOTIATE.asString();
    private static final Logger log = Logger.get(SpnegoAuthentication.class);
    private final String krb5ConfPath;
    private final String serviceName;

    public SpnegoAuthentication(AuthScheme authScheme, String krb5ConfPath, String serviceName, URI serviceUri)
    {
        super(authScheme, serviceUri);

        this.krb5ConfPath = requireNonNull(krb5ConfPath, "krb5ConfPath is null");
        this.serviceName = requireNonNull(serviceName, "serviceName is null");
        System.setProperty("java.security.krb5.conf", krb5ConfPath);
    }

    @Override
    public Result authenticate(Request request, ContentResponse response, HeaderInfo headerInfo, Attributes context)
    {
        boolean caughtException = false;
        GSSContext gssContext = null;
        try {
            gssContext = getGssContext();
            byte[] token = new byte[0];
            if (!gssContext.isEstablished()) {
                token = gssContext.initSecContext(token, 0, token.length);
            }

            if (token != null) {
                log.debug("Successfully established GSSContext with source name: %s and target name: %s",
                        gssContext.getSrcName(),
                        gssContext.getTargName());
                String spnegoToken = NEGOTIATE + " " + String.valueOf(B64Code.encode(token));
                URI requestUri = request.getURI();
                URI baseUri = new URI(requestUri.getScheme(), requestUri.getHost(), null, null);
                return new SpnegoResult(headerInfo.getHeader(), baseUri, spnegoToken);
            }
        }
        catch (URISyntaxException | UnknownHostException | LoginException | GSSException e) {
            caughtException = true;
            throw new AuthenticationException(e);
        }
        finally {
            if (gssContext != null) {
                try {
                    gssContext.dispose();
                }
                catch (GSSException e) {
                    // Wrap and throw the exception only if no one is outstanding.
                    if (!caughtException) {
                        throw new AuthenticationException(e);
                    }
                }
            }
        }

        throw new AuthenticationException(String.format(
                "Failed to establish GSSContext for request %s",
                request.getURI()));
    }

    @Override
    public boolean matches(String type, URI uri, String realm)
    {
        //Realm is not used as for now
        return AuthScheme.NEGOTIATE.toString().equalsIgnoreCase(type) &&
                getServiceUri().getScheme().equalsIgnoreCase(uri.getScheme()) &&
                getServiceUri().getHost().equalsIgnoreCase(uri.getHost());
    }

    private GSSContext getGssContext()
            throws UnknownHostException, LoginException
    {
        String serviceHostName = getServiceUri().getHost();
        String servicePrincipal = KerberosUtil.getServicePrincipal(serviceName, serviceHostName);
        Subject clientSubject = KerberosUtil.getSubject(null, true);
        return KerberosUtil.getGssContext(clientSubject, servicePrincipal, true);
    }

    private static class SpnegoResult
            implements Result
    {
        private final HttpHeader header;
        private final URI uri;
        private final String value;

        public SpnegoResult(HttpHeader header, URI uri, String value)
        {
            this.header = header;
            this.uri = uri;
            this.value = value;
        }

        @Override
        public URI getURI()
        {
            return uri;
        }

        @Override
        public void apply(Request request)
        {
            request.header(header, value);
        }
    }
}
