package io.airlift.log;

import javax.annotation.concurrent.ThreadSafe;

import java.io.IOException;

@ThreadSafe
public interface MessageOutput
{
    void writeMessage(byte[] message)
            throws IOException;

    void flush()
            throws IOException;

    void close()
            throws IOException;
}
