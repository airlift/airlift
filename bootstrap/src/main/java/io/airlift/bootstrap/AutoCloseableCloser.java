package io.airlift.bootstrap;

import com.google.errorprone.annotations.concurrent.GuardedBy;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.util.Objects.requireNonNull;

/**
 * This class is inspired by com.google.common.io.Closer
 */
public final class AutoCloseableCloser
        implements AutoCloseable
{
    @GuardedBy("this")
    private boolean closed;
    @GuardedBy("this")
    private final ArrayList<AutoCloseable> closeables = new ArrayList<>(4);

    private AutoCloseableCloser() {}

    public static AutoCloseableCloser create()
    {
        return new AutoCloseableCloser();
    }

    public <C extends AutoCloseable> C register(C closeable)
    {
        requireNonNull(closeable, "closeable is null");
        boolean registered = false;
        synchronized (this) {
            if (!closed) {
                closeables.add(closeable);
                registered = true;
            }
        }
        if (!registered) {
            IllegalStateException failure = new IllegalStateException("Already closed");
            try {
                closeable.close();
            }
            catch (Exception e) {
                failure.addSuppressed(e);
            }
            throw failure;
        }
        return closeable;
    }

    @Override
    public void close()
            throws Exception
    {
        List<AutoCloseable> closeables;
        synchronized (this) {
            closed = true;
            closeables = List.copyOf(this.closeables.reversed());
            this.closeables.clear();
            this.closeables.trimToSize();
        }
        Throwable rootCause = null;
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            }
            catch (Throwable t) {
                if (rootCause == null) {
                    rootCause = t;
                }
                else if (rootCause != t) {
                    // Self-suppression not permitted
                    rootCause.addSuppressed(t);
                }
            }
        }
        if (rootCause != null) {
            throwIfInstanceOf(rootCause, Exception.class);
            throwIfUnchecked(rootCause);
            // not possible
            throw new AssertionError(rootCause);
        }
    }
}
