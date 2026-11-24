package io.airlift.http.server;

import com.google.common.collect.MapMaker;
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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public final class RequestCancellationServletFilter
        implements Filter
{
    public static final String REQUEST_CANCELLATION_ATTRIBUTE = "io.airlift.request-cancellation";

    private static final ConcurrentMap<Connection, ConnectionCancellationTracker> CONNECTION_TRACKERS = new MapMaker()
            .weakKeys()
            .makeMap();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();
        RequestCancellation state = new RequestCancellation(cancellation);
        request.setAttribute(REQUEST_CANCELLATION_ATTRIBUTE, cancellation);
        Request jettyRequest = getJettyRequest(request);
        boolean cleanupRegistered = jettyRequest != null;
        if (cleanupRegistered) {
            registerJettyListeners(jettyRequest, state);
        }

        try {
            chain.doFilter(request, response);
        }
        finally {
            if (request.isAsyncStarted()) {
                try {
                    request.getAsyncContext().addListener(state);
                }
                catch (IllegalStateException ignored) {
                    state.complete();
                }
            }
            else if (!cleanupRegistered) {
                state.complete();
            }
        }
    }

    private static void registerJettyListeners(Request request, RequestCancellation state)
    {
        request.addFailureListener(state::onFailure);
        registerConnectionCancellation(request.getConnectionMetaData().getConnection(), state);
        Request.addCompletionListener(request, state);
    }

    static void registerJettyListeners(RequestLifecycle request, RequestCancellation state)
    {
        request.addFailureListener(state::onFailure);
        registerConnectionCancellation(request.getConnection(), state);
        request.addCompletionListener(state);
    }

    private static void registerConnectionCancellation(Connection connection, RequestCancellation state)
    {
        if (connection == null) {
            return;
        }
        // Connections can outlive individual requests and multiplex HTTP/2 streams,
        // so register at most one Connection.Listener per Connection and track active cancellations in a concurrent set.
        ConnectionCancellationTracker tracker = CONNECTION_TRACKERS.computeIfAbsent(connection, key -> {
            ConnectionCancellationTracker newTracker = new ConnectionCancellationTracker();
            key.addEventListener(newTracker);
            return newTracker;
        });
        tracker.add(state);
        state.setConnectionTracker(tracker);
    }

    private static Request getJettyRequest(ServletRequest request)
    {
        try {
            return ServletContextRequest.getServletContextRequest(request);
        }
        catch (IllegalStateException ignored) {
            return null;
        }
    }

    static final class RequestCancellation
            implements AsyncListener, Consumer<Throwable>
    {
        private static final VarHandle DONE;

        static {
            try {
                DONE = MethodHandles.lookup().findVarHandle(RequestCancellation.class, "done", boolean.class);
            }
            catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final CompletableFuture<Void> cancellation;
        @SuppressWarnings("unused")
        private volatile boolean done;
        private volatile ConnectionCancellationTracker connectionTracker;

        RequestCancellation(CompletableFuture<Void> cancellation)
        {
            this.cancellation = cancellation;
        }

        void onFailure(Throwable ignored)
        {
            cancel();
        }

        void cancel()
        {
            cancellation.cancel(false);
        }

        void setConnectionTracker(ConnectionCancellationTracker tracker)
        {
            this.connectionTracker = tracker;
            if ((boolean) DONE.getVolatile(this)) {
                tracker.remove(this);
                this.connectionTracker = null;
            }
        }

        void complete()
        {
            if (!DONE.compareAndSet(this, false, true)) {
                return;
            }

            cancellation.complete(null);
            ConnectionCancellationTracker tracker = this.connectionTracker;
            if (tracker != null) {
                tracker.remove(this);
                this.connectionTracker = null;
            }
        }

        @Override
        public void accept(Throwable ignored)
        {
            complete();
        }

        @Override
        public void onComplete(AsyncEvent event)
        {
            complete();
        }

        @Override
        public void onTimeout(AsyncEvent event)
        {
            cancellation.cancel(false);
            complete();
        }

        @Override
        public void onError(AsyncEvent event)
        {
            cancellation.cancel(false);
            complete();
        }

        @Override
        public void onStartAsync(AsyncEvent event)
        {
            event.getAsyncContext().addListener(this);
        }
    }

    static final class ConnectionCancellationTracker
            implements Connection.Listener
    {
        private final Set<RequestCancellation> activeCancellations = ConcurrentHashMap.newKeySet(2);
        private volatile boolean closed;

        void add(RequestCancellation cancellation)
        {
            activeCancellations.add(cancellation);
            if (closed) {
                if (activeCancellations.remove(cancellation)) {
                    cancellation.cancel();
                }
            }
        }

        void remove(RequestCancellation cancellation)
        {
            activeCancellations.remove(cancellation);
        }

        int activeCount()
        {
            return activeCancellations.size();
        }

        @Override
        public void onClosed(Connection connection)
        {
            closed = true;
            if (connection != null) {
                CONNECTION_TRACKERS.remove(connection, this);
            }
            Iterator<RequestCancellation> iterator = activeCancellations.iterator();
            while (iterator.hasNext()) {
                RequestCancellation cancellation = iterator.next();
                iterator.remove();
                cancellation.cancel();
            }
        }
    }

    interface RequestLifecycle
    {
        void addFailureListener(Consumer<Throwable> listener);

        Connection getConnection();

        void addCompletionListener(Consumer<Throwable> listener);
    }
}
