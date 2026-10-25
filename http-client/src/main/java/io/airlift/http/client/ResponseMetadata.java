package io.airlift.http.client;

import com.google.common.collect.ListMultimap;

import java.util.List;
import java.util.Optional;

/**
 * Status line and headers of a response.
 */
public interface ResponseMetadata
{
    HttpVersion getHttpVersion();

    int getStatusCode();

    default Optional<String> getHeader(HeaderName name)
    {
        List<String> values = getHeaders(name);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    default List<String> getHeaders(HeaderName name)
    {
        return getHeaders().get(name);
    }

    ListMultimap<HeaderName, String> getHeaders();
}
