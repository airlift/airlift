package io.airlift.http.server;

import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;

public class RejectForwardedRequestCustomizer
        implements HttpConfiguration.Customizer
{
    private static final String X_FORWARDED_PREFIX = "x-forwarded-";

    @Override
    public Request customize(Request request, HttpFields.Mutable responseHeaders)
    {
        for (HttpField httpHeader : request.getHeaders()) {
            if (isForwardingHeader(httpHeader)) {
                throw new BadMessageException(HttpStatus.NOT_ACCEPTABLE_406, "Server configuration does not allow processing of the %s header".formatted(httpHeader.getName()));
            }
        }
        return request;
    }

    private static boolean isForwardingHeader(HttpField httpField)
    {
        return httpField.getName().regionMatches(true, 0, X_FORWARDED_PREFIX, 0, X_FORWARDED_PREFIX.length());
    }
}
