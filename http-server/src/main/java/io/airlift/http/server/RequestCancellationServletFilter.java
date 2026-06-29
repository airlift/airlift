package io.airlift.http.server;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.eclipse.jetty.ee11.servlet.ServletContextRequest;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.Request;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RequestCancellationServletFilter
        implements Filter
{
    public static final String REQUEST_CANCELLATION_ATTRIBUTE = "io.airlift.request-cancellation";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();
        CancellationCleanup cleanup = new CancellationCleanup(cancellation);
        request.setAttribute(REQUEST_CANCELLATION_ATTRIBUTE, cancellation);
        boolean cleanupRegistered = getJettyRequest(request)
                .map(jettyRequest -> {
                    registerJettyListeners(jettyRequest, cancellation, cleanup);
                    return true;
                })
                .orElse(false);

        try {
            chain.doFilter(request, response);
        }
        finally {
            if (request.isAsyncStarted()) {
                try {
                    request.getAsyncContext().addListener(new CancellationAsyncListener(cancellation, cleanup));
                }
                catch (IllegalStateException ignored) {
                    cleanup.run();
                }
            }
            else if (!cleanupRegistered) {
                cleanup.run();
            }
        }
    }

    private static void registerJettyListeners(Request request, CompletableFuture<Void> cancellation, CancellationCleanup cleanup)
    {
        registerJettyListeners(new JettyRequestLifecycle(request), cancellation, cleanup);
    }

    static void registerJettyListeners(RequestLifecycle request, CompletableFuture<Void> cancellation, CancellationCleanup cleanup)
    {
        request.addFailureListener(_ -> cancellation.cancel(false));
        Connection connection = request.getConnection();
        if (connection != null) {
            // Connections can outlive individual requests, so this listener is weak and is removed on request completion.
            WeakCancellationListener listener = new WeakCancellationListener(cancellation);
            connection.addEventListener(listener);
            cleanup.setConnectionListener(new ConnectionListenerRegistration(connection, listener));
        }
        request.addCompletionListener(_ -> cleanup.run());
    }

    private static Optional<Request> getJettyRequest(ServletRequest request)
    {
        try {
            return Optional.of(ServletContextRequest.getServletContextRequest(request));
        }
        catch (IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    static class CancellationCleanup
            implements Runnable
    {
        private final CompletableFuture<Void> cancellation;
        private final AtomicBoolean done = new AtomicBoolean();
        private volatile Optional<ConnectionListenerRegistration> connectionListener = Optional.empty();

        CancellationCleanup(CompletableFuture<Void> cancellation)
        {
            this.cancellation = cancellation;
        }

        private void setConnectionListener(ConnectionListenerRegistration connectionListener)
        {
            this.connectionListener = Optional.of(connectionListener);
            if (done.get()) {
                connectionListener.close();
            }
        }

        @Override
        public void run()
        {
            if (!done.compareAndSet(false, true)) {
                return;
            }

            cancellation.complete(null);
            connectionListener.ifPresent(ConnectionListenerRegistration::close);
        }
    }

    private record ConnectionListenerRegistration(Connection connection, Connection.Listener listener)
            implements AutoCloseable
    {
        @Override
        public void close()
        {
            connection.removeEventListener(listener);
        }
    }

    interface RequestLifecycle
    {
        void addFailureListener(Consumer<Throwable> listener);

        Connection getConnection();

        void addCompletionListener(Consumer<Throwable> listener);
    }

    private record JettyRequestLifecycle(Request request)
            implements RequestLifecycle
    {
        @Override
        public void addFailureListener(Consumer<Throwable> listener)
        {
            request.addFailureListener(listener);
        }

        @Override
        public Connection getConnection()
        {
            return request.getConnectionMetaData().getConnection();
        }

        @Override
        public void addCompletionListener(Consumer<Throwable> listener)
        {
            Request.addCompletionListener(request, listener);
        }
    }

    record WeakCancellationListener(WeakReference<CompletableFuture<Void>> cancellation)
            implements Connection.Listener
    {
        WeakCancellationListener(CompletableFuture<Void> cancellation)
        {
            this(new WeakReference<>(cancellation));
        }

        @Override
        public void onClosed(Connection connection)
        {
            CompletableFuture<Void> cancellation = this.cancellation.get();
            if (cancellation != null) {
                cancellation.cancel(false);
            }
        }
    }

    record CancellationAsyncListener(CompletableFuture<Void> cancellation, CancellationCleanup cleanup)
            implements AsyncListener
    {
        @Override
        public void onComplete(AsyncEvent event)
        {
            cleanup.run();
        }

        @Override
        public void onTimeout(AsyncEvent event)
        {
            cancellation.cancel(false);
            cleanup.run();
        }

        @Override
        public void onError(AsyncEvent event)
        {
            cancellation.cancel(false);
            cleanup.run();
        }

        @Override
        public void onStartAsync(AsyncEvent event)
        {
            event.getAsyncContext().addListener(this);
        }
    }
}
