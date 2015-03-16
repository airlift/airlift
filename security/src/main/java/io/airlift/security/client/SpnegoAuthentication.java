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

import static com.google.common.base.Preconditions.checkNotNull;

public class SpnegoAuthentication
        extends JettyAuthentication
{
    private static final String NEGOTIATE = HttpHeader.NEGOTIATE.asString();
    private static final Logger log = Logger.get(SpnegoAuthentication.class);
    private final String krb5Conf;
    private final String serviceName;
    private GSSContext gssContext = null;

    public SpnegoAuthentication(AuthScheme authScheme, String krb5Conf, String serviceName, URI serviceUri)
    {
        super(authScheme, serviceUri);

        checkNotNull(krb5Conf, "krb5Conf is null");
        checkNotNull(serviceName, "serviceName is null");
        this.krb5Conf = krb5Conf;
        this.serviceName = serviceName;
    }

    @Override
    public Result authenticate(Request request, ContentResponse response, HeaderInfo headerInfo, Attributes context)
    {
        byte[] inToken = new byte[0];
        try {
            gssContext = getGssContext();

            if (!gssContext.isEstablished()) {
                inToken = gssContext.initSecContext(inToken, 0, inToken.length);
            }

            if (inToken.length > 0) {
                log.debug("Successfully established GSSContext with source name: %s and target name: %s",
                        gssContext.getSrcName(),
                        gssContext.getTargName());
                String spnegoToken = NEGOTIATE + " " + String.valueOf(B64Code.encode(inToken));
                URI requestUri = request.getURI();
                URI baseUri = new URI(requestUri.getScheme(), requestUri.getHost(), null, null);
                return new SpnegoResult(headerInfo.getHeader(), baseUri, spnegoToken);
            }
            else {
                log.warn("Failed to establish GSSContext for request %s!", request.getURI());
            }
            gssContext.dispose();
            return  null;
        } catch (URISyntaxException | UnknownHostException | LoginException | GSSException e) {
            throw new AuthenticationException(e);
        }
    }

    @Override
    public boolean matches(String type, URI uri, String realm)
    {
        //Realm is not used as for now
        boolean match = true;
        match &= AuthScheme.NEGOTIATE.toString().equalsIgnoreCase(type);
        match &= getServiceUri().getScheme().equalsIgnoreCase(uri.getScheme());
        match &= getServiceUri().getHost().equalsIgnoreCase(uri.getHost());
        return match;
    }

    private GSSContext getGssContext()
            throws UnknownHostException, LoginException
    {
        String serviceHostName = getServiceUri().getHost();
        String servicePrincipal = KerberosUtil.getServicePrincipal(serviceName, serviceHostName);
        Subject clientSubject = KerberosUtil.getSubject(null, krb5Conf, true);
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
