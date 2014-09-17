package com.proofpoint.testing;

import java.io.Closeable;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public class Closeables
{
    private Closeables()
    {
    }

    public static void closeQuietly(Closeable... closeables)
    {
        if (closeables == null) {
            return;
        }
        for (Closeable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            }
            catch (IOException | RuntimeException ignored) {
            }
        }
    }

    public static void closeAll(Closeable... closeables)
            throws IOException
    {
        if (closeables == null) {
            return;
        }
        IOException rootCause = null;
        for (Closeable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            }
            catch (IOException e) {
                if (rootCause == null) {
                    rootCause = e;
                }
                else {
                    rootCause.addSuppressed(e);
                }
            }
            catch (Throwable e) {
                if (rootCause == null) {
                    rootCause = new IOException(e);
                }
                else {
                    rootCause.addSuppressed(e);
                }
            }
        }
        if (rootCause != null) {
            throw rootCause;
        }
    }

    public static void closeAllRuntimeException(Closeable... closeables)
    {
        if (closeables == null) {
            return;
        }
        RuntimeException rootCause = null;
        for (Closeable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            }
            catch (Throwable e) {
                if (rootCause == null) {
                    rootCause = new RuntimeException(e);
                }
                else {
                    rootCause.addSuppressed(e);
                }
            }
        }
        if (rootCause != null) {
            throw rootCause;
        }
    }

    public static <T extends Throwable> T closeAllSuppress(T rootCause, Closeable... closeables)
    {
        checkNotNull(rootCause, "rootCause is null");
        if (closeables == null) {
            return rootCause;
        }
        for (Closeable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            }
            catch (Throwable e) {
                rootCause.addSuppressed(e);
            }
        }
        return rootCause;
    }
}
