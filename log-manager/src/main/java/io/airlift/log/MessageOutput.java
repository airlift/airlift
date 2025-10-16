package io.airlift.log;

import com.google.errorprone.annotations.ThreadSafe;
import java.io.IOException;

@ThreadSafe
public interface MessageOutput {
    void writeMessage(byte[] message) throws IOException;

    void flush() throws IOException;

    void close() throws IOException;
}
