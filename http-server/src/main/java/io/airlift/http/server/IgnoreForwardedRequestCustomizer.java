package io.airlift.http.server;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;

import java.util.Set;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

public class IgnoreForwardedRequestCustomizer
        implements HttpConfiguration.Customizer
{
    private static final String X_FORWARDED_PREFIX = "x-forwarded-";

    @Override
    public Request customize(Request request, HttpFields.Mutable responseHeaders)
    {
        Set<HttpField> headersToRemove = request.getHeaders().stream()
                .filter(IgnoreForwardedRequestCustomizer::isForwardingHeader)
                .collect(toImmutableSet());

        HttpFields original = request.getHeaders();
        HttpFields.Mutable builder = HttpFields.build(original.size() - headersToRemove.size());
        original.forEach(httpField -> {
            if (!headersToRemove.contains(httpField)) {
                builder.add(httpField);
            }
        });

        final HttpFields headers = builder.asImmutable();

        return new Request.Wrapper(request) {
            public HttpFields getHeaders()
            {
                return headers;
            }
        };
    }

    private static boolean isForwardingHeader(HttpField httpField)
    {
        return httpField.getName().regionMatches(true, 0, X_FORWARDED_PREFIX, 0, X_FORWARDED_PREFIX.length());
    }
}
