package io.airlift.http.server;

import jakarta.servlet.ServletRequest;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.airlift.http.server.RequestCancellationServletFilter.REQUEST_CANCELLATION_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;

public class TestRequestCancellationServletFilter
{
    @Test
    public void testRequestCancellationIsStoredBeforeFilterChainRuns()
            throws Exception
    {
        ServletRequest request = testingServletRequest();
        AtomicReference<Object> chainCancellation = new AtomicReference<>();

        new RequestCancellationServletFilter().doFilter(
                request,
                null,
                (servletRequest, _) -> chainCancellation.set(servletRequest.getAttribute(REQUEST_CANCELLATION_ATTRIBUTE)));

        assertThat(chainCancellation.get()).isInstanceOf(CompletableFuture.class);
    }

    @Test
    public void testJettyFailureListenerCancelsRequest()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();
        TestingRequestLifecycle request = new TestingRequestLifecycle(new TestingConnection());

        RequestCancellationServletFilter.registerJettyListeners(
                request,
                cancellation,
                new RequestCancellationServletFilter.CancellationCleanup(cancellation));

        request.failureListener.get().accept(new RuntimeException("request failed"));

        assertThat(cancellation).isCancelled();
    }

    @Test
    public void testConnectionCloseListenerCancelsRequest()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();
        TestingConnection connection = new TestingConnection();

        RequestCancellationServletFilter.registerJettyListeners(
                new TestingRequestLifecycle(connection),
                cancellation,
                new RequestCancellationServletFilter.CancellationCleanup(cancellation));

        connection.listeners.getFirst().onClosed(connection);

        assertThat(cancellation).isCancelled();
    }

    @Test
    public void testRequestCompletionRemovesConnectionListener()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();
        TestingConnection connection = new TestingConnection();
        TestingRequestLifecycle request = new TestingRequestLifecycle(connection);

        RequestCancellationServletFilter.registerJettyListeners(
                request,
                cancellation,
                new RequestCancellationServletFilter.CancellationCleanup(cancellation));

        assertThat(connection.listeners).hasSize(1);
        Connection.Listener connectionListener = connection.listeners.getFirst();

        request.completionListener.get().accept(null);
        connectionListener.onClosed(connection);
        connection.close();

        assertThat(connection.listeners).isEmpty();
        assertThat(cancellation).isCompleted();
        assertThat(cancellation).isNotCancelled();
    }

    @Test
    public void testRequestCompletionWinsRaceWithConnectionClose()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();
        TestingConnection connection = new TestingConnection();
        TestingRequestLifecycle request = new TestingRequestLifecycle(connection);

        RequestCancellationServletFilter.registerJettyListeners(
                request,
                cancellation,
                new RequestCancellationServletFilter.CancellationCleanup(cancellation));

        connection.closeDuringListenerRemoval = true;
        request.completionListener.get().accept(null);

        assertThat(connection.listeners).isEmpty();
        assertThat(cancellation).isCompleted();
        assertThat(cancellation).isNotCancelled();
    }

    @Test
    public void testAsyncTimeoutCancelsRequest()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();

        new RequestCancellationServletFilter.CancellationAsyncListener(
                cancellation,
                new RequestCancellationServletFilter.CancellationCleanup(cancellation))
                .onTimeout(null);

        assertThat(cancellation).isCancelled();
    }

    @Test
    public void testAsyncErrorCancelsRequest()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();

        new RequestCancellationServletFilter.CancellationAsyncListener(
                cancellation,
                new RequestCancellationServletFilter.CancellationCleanup(cancellation))
                .onError(null);

        assertThat(cancellation).isCancelled();
    }

    @Test
    public void testConnectionCloseCancelsRequest()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();

        new RequestCancellationServletFilter.WeakCancellationListener(cancellation)
                .onClosed(null);

        assertThat(cancellation).isCancelled();
    }

    private static ServletRequest testingServletRequest()
    {
        Map<String, Object> attributes = new HashMap<>();
        return (ServletRequest) Proxy.newProxyInstance(
                TestRequestCancellationServletFilter.class.getClassLoader(),
                new Class<?>[] {ServletRequest.class},
                (_, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> attributes.get((String) args[0]);
                    case "getAttributeNames" -> Collections.enumeration(attributes.keySet());
                    case "setAttribute" -> {
                        attributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "removeAttribute" -> {
                        attributes.remove((String) args[0]);
                        yield null;
                    }
                    case "isAsyncStarted" -> false;
                    case "getAsyncContext" -> throw new IllegalStateException("async is not started");
                    case "toString" -> "testing servlet request";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType)
    {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0.0;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        throw new IllegalArgumentException("Unexpected primitive return type: " + returnType);
    }

    private static class TestingRequestLifecycle
            implements RequestCancellationServletFilter.RequestLifecycle
    {
        private final Connection connection;
        private final AtomicReference<Consumer<Throwable>> failureListener = new AtomicReference<>();
        private final AtomicReference<Consumer<Throwable>> completionListener = new AtomicReference<>();

        private TestingRequestLifecycle(Connection connection)
        {
            this.connection = connection;
        }

        @Override
        public void addFailureListener(Consumer<Throwable> listener)
        {
            failureListener.set(listener);
        }

        @Override
        public Connection getConnection()
        {
            return connection;
        }

        @Override
        public void addCompletionListener(Consumer<Throwable> listener)
        {
            completionListener.set(listener);
        }
    }

    private static class TestingConnection
            implements Connection
    {
        private final List<Listener> listeners = new ArrayList<>();
        private boolean closeDuringListenerRemoval;

        @Override
        public void addEventListener(EventListener listener)
        {
            listeners.add((Listener) listener);
        }

        @Override
        public void removeEventListener(EventListener listener)
        {
            if (closeDuringListenerRemoval) {
                ((Listener) listener).onClosed(this);
            }
            listeners.remove(listener);
        }

        @Override
        public void onOpen() {}

        @Override
        public void onClose(Throwable cause) {}

        @Override
        public EndPoint getEndPoint()
        {
            return null;
        }

        @Override
        public void close()
        {
            List.copyOf(listeners).forEach(listener -> listener.onClosed(this));
        }

        @Override
        public boolean onIdleExpired(TimeoutException timeoutException)
        {
            return false;
        }

        @Override
        public long getMessagesIn()
        {
            return 0;
        }

        @Override
        public long getMessagesOut()
        {
            return 0;
        }

        @Override
        public long getBytesIn()
        {
            return 0;
        }

        @Override
        public long getBytesOut()
        {
            return 0;
        }

        @Override
        public long getCreatedTimeStamp()
        {
            return 0;
        }
    }
}
