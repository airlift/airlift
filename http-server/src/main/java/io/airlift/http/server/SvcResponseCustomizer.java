package io.airlift.http.server;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;

import static java.lang.String.format;

public class SvcResponseCustomizer
        implements HttpConfiguration.Customizer
{
    private final PreEncodedHttpField altSvcHttpField;

    public SvcResponseCustomizer(int quicPort)
    {
        altSvcHttpField = new PreEncodedHttpField(HttpHeader.ALT_SVC, format("h3=\":%d\"", quicPort));
    }

    @Override
    public Request customize(Request request, HttpFields.Mutable responseHeaders)
    {
        responseHeaders.add(altSvcHttpField);
        return request;
    }
}
