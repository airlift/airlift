package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

import java.net.URI;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Objects.toStringHelper;

@Beta
public class Request
{
    private final URI uri;
    private final String method;
    private final ListMultimap<String, String> headers;
    private final BodyGenerator bodyGenerator;

    public Request(URI uri, String method, ListMultimap<String, String> headers, BodyGenerator bodyGenerator)
    {
        Preconditions.checkNotNull(uri, "uri is null");
        Preconditions.checkNotNull(method, "method is null");

        this.uri = uri;
        this.method = method;
        this.headers = ImmutableListMultimap.copyOf(headers);
        this.bodyGenerator = bodyGenerator;
    }

    public URI getUri()
    {
        return uri;
    }

    public String getMethod()
    {
        return method;
    }

    public ListMultimap<String, String> getHeaders()
    {
        return headers;
    }

    public BodyGenerator getBodyGenerator()
    {
        return bodyGenerator;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("uri", uri)
                .add("method", method)
                .add("headers", headers)
                .add("bodyGenerator", bodyGenerator)
                .toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Request)) {
            return false;
        }
        Request r = (Request) o;
        return equal(uri, r.uri) &&
                equal(method, r.method) &&
                equal(headers, r.headers) &&
                equal(bodyGenerator, r.bodyGenerator);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(uri, method, headers, bodyGenerator);
    }
}
