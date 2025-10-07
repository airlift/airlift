package io.airlift.api;

import jakarta.ws.rs.core.Request;

import java.util.Optional;

@FunctionalInterface
public interface ApiIdLookup<T extends ApiId<?, ?>>
{
    Optional<T> lookup(Request request, String requestValue);
}
