package io.airlift.testing;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public final class Closeables
{
    private Closeables()
    {
    }

    public static void closeQuietly(AutoCloseable... closeables)
    {
        if (closeables == null) {
            return;
        }
        ImmutableList.Builder<Throwable> exceptions = ImmutableList.builder();
        for (AutoCloseable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exceptions.add(e);
            }
            catch (Exception ignored) {
            }
        }
        rethrowAsRuntime(exceptions.build());
    }

    public static void closeAll(AutoCloseable... closeables)
            throws Exception
    {
        if (closeables == null) {
            return;
        }
        ImmutableList.Builder<Throwable> exceptions = ImmutableList.builder();
        for (AutoCloseable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exceptions.add(e);
            }
            catch (Exception e) {
                exceptions.add(e);
            }
            catch (Throwable e) {
                exceptions.add(e);
            }
        }
        rethrow(exceptions.build());
    }

    public static void closeAllRuntimeException(AutoCloseable... closeables)
    {
        if (closeables == null) {
            return;
        }
        ImmutableList.Builder<Throwable> exceptions = ImmutableList.builder();
        for (AutoCloseable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            }
            catch (Throwable e) {
                exceptions.add(e);
            }
        }
        rethrowAsRuntime(exceptions.build());
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
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (rootCause != e) {
                    rootCause.addSuppressed(e);
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

    private static void rethrowAsRuntime(List<Throwable> exceptions)
    {
        Optional<RuntimeException> rootException = exceptions.stream()
                .filter(RuntimeException.class::isInstance)
                .map(RuntimeException.class::cast)
                .findFirst();

        if (!rootException.isPresent() && !exceptions.isEmpty()) {
            rootException = Optional.of(new RuntimeException());
        }

        if (rootException.isPresent()) {
            addSuppressed(rootException.get(), exceptions);
            throw rootException.get();
        }
    }

    private static <E extends Exception> void rethrow(List<Throwable> exceptions)
            throws Exception
    {
        Optional<Exception> rootException = exceptions.stream()
                .filter(InterruptedException.class::isInstance)
                .map(Exception.class::cast)
                .findFirst();

        if (!rootException.isPresent()) {
            rootException = exceptions.stream()
                    .filter(Exception.class::isInstance)
                    .map(Exception.class::cast)
                    .findFirst();
        }

        if (!rootException.isPresent() && !exceptions.isEmpty()) {
            rootException = Optional.of(new Exception());
        }

        if (rootException.isPresent()) {
            addSuppressed(rootException.get(), exceptions);
            throw rootException.get();
        }
    }

    private static void addSuppressed(Exception rootException, List<Throwable> exceptions)
    {
        for (Throwable exception : exceptions) {
            if (rootException != exception) {
                // Self-suppression not permitted
                rootException.addSuppressed(exception);
            }
        }
    }
}
