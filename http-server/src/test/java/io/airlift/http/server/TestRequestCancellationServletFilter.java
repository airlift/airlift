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
                new RequestCancellationServletFilter.RequestCancellation(cancellation));

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
                new RequestCancellationServletFilter.RequestCancellation(cancellation));

        connection.listeners.getFirst().onClosed(connection);

        assertThat(cancellation).isCancelled();
    }

    @Test
    public void testMultipleRequestsShareSingleConnectionListener()
    {
        TestingConnection connection = new TestingConnection();
        CompletableFuture<Void> firstCancellation = new CompletableFuture<>();
        CompletableFuture<Void> secondCancellation = new CompletableFuture<>();
        TestingRequestLifecycle firstRequest = new TestingRequestLifecycle(connection);
        TestingRequestLifecycle secondRequest = new TestingRequestLifecycle(connection);

        RequestCancellationServletFilter.registerJettyListeners(
                firstRequest,
                new RequestCancellationServletFilter.RequestCancellation(firstCancellation));
        RequestCancellationServletFilter.registerJettyListeners(
                secondRequest,
                new RequestCancellationServletFilter.RequestCancellation(secondCancellation));

        assertThat(connection.listeners).hasSize(1);
        RequestCancellationServletFilter.ConnectionCancellationTracker tracker =
                (RequestCancellationServletFilter.ConnectionCancellationTracker) connection.listeners.getFirst();
        assertThat(tracker.activeCount()).isEqualTo(2);

        // Complete first request: should untrack only first request without mutating connection listeners
        firstRequest.completionListener.get().accept(null);
        assertThat(connection.listeners).hasSize(1);
        assertThat(tracker.activeCount()).isEqualTo(1);
        assertThat(firstCancellation).isCompleted();
        assertThat(firstCancellation).isNotCancelled();

        // Closing the connection cancels only the still-active second request
        connection.close();
        assertThat(tracker.activeCount()).isZero();
        assertThat(firstCancellation).isCompleted();
        assertThat(firstCancellation).isNotCancelled();
        assertThat(secondCancellation).isCancelled();
    }

    @Test
    public void testRequestCompletionRemovesActiveCancellation()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();
        TestingConnection connection = new TestingConnection();
        TestingRequestLifecycle request = new TestingRequestLifecycle(connection);

        RequestCancellationServletFilter.registerJettyListeners(
                request,
                new RequestCancellationServletFilter.RequestCancellation(cancellation));

        assertThat(connection.listeners).hasSize(1);
        RequestCancellationServletFilter.ConnectionCancellationTracker tracker =
                (RequestCancellationServletFilter.ConnectionCancellationTracker) connection.listeners.getFirst();
        assertThat(tracker.activeCount()).isEqualTo(1);

        request.completionListener.get().accept(null);
        assertThat(tracker.activeCount()).isZero();

        tracker.onClosed(connection);
        connection.close();

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
                new RequestCancellationServletFilter.RequestCancellation(cancellation));

        request.completionListener.get().accept(null);
        connection.close();

        assertThat(cancellation).isCompleted();
        assertThat(cancellation).isNotCancelled();
    }

    @Test
    public void testAddAfterConnectionCloseCancelsImmediately()
    {
        RequestCancellationServletFilter.ConnectionCancellationTracker tracker =
                new RequestCancellationServletFilter.ConnectionCancellationTracker();
        tracker.onClosed(null);

        CompletableFuture<Void> cancellation = new CompletableFuture<>();
        tracker.add(new RequestCancellationServletFilter.RequestCancellation(cancellation));

        assertThat(cancellation).isCancelled();
        assertThat(tracker.activeCount()).isZero();
    }

    @Test
    public void testAsyncTimeoutCancelsRequest()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();

        new RequestCancellationServletFilter.RequestCancellation(cancellation)
                .onTimeout(null);

        assertThat(cancellation).isCancelled();
    }

    @Test
    public void testAsyncErrorCancelsRequest()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();

        new RequestCancellationServletFilter.RequestCancellation(cancellation)
                .onError(null);

        assertThat(cancellation).isCancelled();
    }

    @Test
    public void testConnectionCloseCancelsRequest()
    {
        CompletableFuture<Void> cancellation = new CompletableFuture<>();
        RequestCancellationServletFilter.ConnectionCancellationTracker tracker =
                new RequestCancellationServletFilter.ConnectionCancellationTracker();
        tracker.add(new RequestCancellationServletFilter.RequestCancellation(cancellation));

        tracker.onClosed(null);

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

        @Override
        public void addEventListener(EventListener listener)
        {
            listeners.add((Listener) listener);
        }

        @Override
        public void removeEventListener(EventListener listener)
        {
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
