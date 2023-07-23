package io.airlift.http.client;

import java.io.Closeable;
import java.util.Iterator;

public interface StreamingResponseIterator<T>
        extends Iterator<T>, Response, Closeable
{
    @Override
    void close();
}
