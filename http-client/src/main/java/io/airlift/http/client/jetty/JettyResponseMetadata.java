package io.airlift.http.client.jetty;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ListMultimap;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpVersion;
import io.airlift.http.client.ResponseMetadata;
import org.eclipse.jetty.client.Response;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.http.client.Response.toHeadersMap;
import static java.util.Objects.requireNonNull;

class JettyResponseMetadata
        implements ResponseMetadata
{
    private final Response response;
    private final Supplier<ListMultimap<HeaderName, String>> headers;

    public JettyResponseMetadata(Response response)
    {
        this.response = requireNonNull(response, "response is null");
        this.headers = Suppliers.memoize(() -> toHeadersMap(response.getHeaders()));
    }

    @Override
    public int getStatusCode()
    {
        return response.getStatus();
    }

    @Override
    public HttpVersion getHttpVersion()
    {
        return switch (response.getVersion()) {
            case HTTP_0_9, HTTP_1_0, HTTP_1_1 -> HttpVersion.HTTP_1;
            case HTTP_2 -> HttpVersion.HTTP_2;
            case HTTP_3 -> HttpVersion.HTTP_3;
        };
    }

    @Override
    public ListMultimap<HeaderName, String> getHeaders()
    {
        return headers.get();
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("statusCode", getStatusCode())
                .add("headers", getHeaders())
                .toString();
    }
}
