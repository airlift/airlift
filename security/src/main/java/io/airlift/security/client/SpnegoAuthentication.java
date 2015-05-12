package io.airlift.security.client;

import io.airlift.log.Logger;
import io.airlift.security.exception.AuthenticationException;
import io.airlift.security.utils.CloseableGSSContext;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.Attributes;
import org.ietf.jgss.GSSException;

import java.net.URI;
import java.util.Base64;

import static java.util.Objects.requireNonNull;

public class SpnegoAuthentication
        implements Authentication
{
    private static final String NEGOTIATE = HttpHeader.NEGOTIATE.asString();
    private static final Logger log = Logger.get(SpnegoAuthentication.class);
    private final String serviceName;

    public SpnegoAuthentication(String krb5ConfPath, String serviceName)
    {
        requireNonNull(krb5ConfPath, "krb5ConfPath is null");
        this.serviceName = requireNonNull(serviceName, "serviceName is null");

        System.setProperty("java.security.krb5.conf", krb5ConfPath);
    }

    @Override
    public Result authenticate(Request request, ContentResponse response, HeaderInfo headerInfo, Attributes context)
    {
        URI requestUri = request.getURI();
        try (CloseableGSSContext gssContext = new CloseableGSSContext(serviceName, requestUri)) {
            // We generate the token once and send it to the peer.
            byte[] token = gssContext.getContext().initSecContext(new byte[0], 0, 0);
            if (token != null) {
                log.debug("Successfully established GSSContext with source name: %s and target name: %s",
                        gssContext.getContext().getSrcName(),
                        gssContext.getContext().getTargName());
                String tokenValue = String.format("%s %s", NEGOTIATE, Base64.getEncoder().encodeToString(token));
                URI baseUri = UrlUtil.normalizedUri(requestUri);
                return new SpnegoResult(headerInfo.getHeader(), baseUri, tokenValue);
            }

            throw new AuthenticationException(String.format("Failed to establish GSSContext for request %s", requestUri));
        }
        catch (GSSException e) {
            throw new AuthenticationException(e);
        }
    }

    @Override
    public boolean matches(String type, URI uri, String realm)
    {
        // The class matches all requests for Negotiate scheme.Realm is not used as for now
        return NEGOTIATE.equalsIgnoreCase(type);
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
