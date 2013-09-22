package io.airlift.testing;

import javax.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

public class Closeables
{
    public static void closeQuietly(@Nullable Closeable closeable)
    {
        try {
            if (closeable != null) {
                closeable.close();
            }
        }
        catch (IOException ignored) {
        }
    }
}
