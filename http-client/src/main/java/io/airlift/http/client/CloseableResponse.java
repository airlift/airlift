package io.airlift.http.client;

public interface CloseableResponse
        extends Response, AutoCloseable
{
    @Override
    void close();
}
