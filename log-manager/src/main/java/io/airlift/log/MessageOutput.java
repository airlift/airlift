package io.airlift.log;

import java.io.IOException;

public interface MessageOutput
{
    void writeMessage(byte[] message)
            throws IOException;

    void flush()
            throws IOException;

    void close()
            throws IOException;
}
