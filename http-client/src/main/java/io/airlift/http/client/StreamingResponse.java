package io.airlift.http.client;

import java.io.Closeable;

public interface StreamingResponse extends Response, Closeable {
    @Override
    void close();
}
