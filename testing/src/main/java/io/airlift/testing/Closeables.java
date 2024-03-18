package io.airlift.testing;

import java.io.Closeable;
import java.io.IOException;

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.util.Objects.requireNonNull;

public final class Closeables
{
    private Closeables()
    {
    }

    /**
     * @deprecated Usage of this method is discouraged, as it may hide real problems.
     * Use {@link #closeAll(Closeable...)} instead and, if necessary, suppress exceptions
     * explicitly.
     */
    @Deprecated
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
        try {
            closeAll((AutoCloseable[]) closeables);
        }
        catch (Exception e) {
            throwIfInstanceOf(e, IOException.class);
            throwIfUnchecked(e);
            // Unreachable
            throw new RuntimeException(e);
        }
    }

    public static void closeAll(AutoCloseable... closeables)
            throws Exception
    {
        if (closeables == null) {
            return;
        }
        Throwable rootCause = null;
        for (AutoCloseable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            }
            catch (Throwable e) {
                if (rootCause == null) {
                    rootCause = e;
                }
                else if (rootCause != e) {
                    // Self-suppression not permitted
                    rootCause.addSuppressed(e);
                }
            }
        }
        if (rootCause != null) {
            throwIfUnchecked(rootCause);
            throwIfInstanceOf(rootCause, Exception.class);
            throw new RuntimeException(rootCause);
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
                else if (rootCause != e) {
                    // Self-suppression not permitted
                    rootCause.addSuppressed(e);
                }
            }
        }
        if (rootCause != null) {
            throw rootCause;
        }
    }

    public static <T extends Throwable> T closeAllSuppress(T rootCause, AutoCloseable... closeables)
    {
        requireNonNull(rootCause, "rootCause is null");
        if (closeables == null) {
            return rootCause;
        }
        for (AutoCloseable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            }
            catch (Throwable e) {
                // Self-suppression not permitted
                if (rootCause != e) {
                    rootCause.addSuppressed(e);
                }
            }
        }
        return rootCause;
    }
}
