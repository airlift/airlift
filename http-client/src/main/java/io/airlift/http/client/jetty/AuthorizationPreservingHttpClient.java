package io.airlift.http.client.jetty;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.http.HttpField;

import java.net.URI;

import static java.lang.Boolean.TRUE;
import static org.eclipse.jetty.http.HttpHeader.AUTHORIZATION;

class AuthorizationPreservingHttpClient
        extends HttpClient
{
    private static final String PRESERVE_AUTHORIZATION_KEY = "airlift_preserve_authorization";

    public AuthorizationPreservingHttpClient(HttpClientTransport transport)
    {
        super(transport);
    }

    @Override
    protected Request copyRequest(Request oldRequest, URI newUri)
    {
        Request newRequest = super.copyRequest(oldRequest, newUri);

        if (isPreserveAuthorization(oldRequest)) {
            setPreserveAuthorization(newRequest, true);
            for (HttpField field : oldRequest.getHeaders().getFields(AUTHORIZATION)) {
                newRequest.headers(headers -> headers.add(field));
            }
        }

        return newRequest;
    }

    public static void setPreserveAuthorization(Request request, boolean preserveAuthorization)
    {
        request.attribute(PRESERVE_AUTHORIZATION_KEY, preserveAuthorization);
    }

    private static boolean isPreserveAuthorization(Request request)
    {
        return TRUE.equals(request.getAttributes().get(PRESERVE_AUTHORIZATION_KEY));
    }
}
