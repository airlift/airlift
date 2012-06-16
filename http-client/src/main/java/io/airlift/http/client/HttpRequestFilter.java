package io.airlift.http.client;

public interface HttpRequestFilter
{
    Request filterRequest(Request request);
}
