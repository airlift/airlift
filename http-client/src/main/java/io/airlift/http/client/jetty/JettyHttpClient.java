package io.airlift.http.client.jetty;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AbstractFuture;
import io.airlift.http.client.BodyGenerator;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.ResponseTooLargeException;
import io.airlift.http.client.StaticBodyGenerator;
import io.airlift.log.Logger;
import io.airlift.stats.Distribution;
import io.airlift.units.DataSize;
import io.airlift.units.DataSize.Unit;
import io.airlift.units.Duration;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.PoolingHttpDestination;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Destination;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Response.Listener;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class JettyHttpClient
        implements io.airlift.http.client.HttpClient
{
    private static final AtomicLong nameCounter = new AtomicLong();

    private final HttpClient httpClient;
    private final long maxContentLength;
    private final long requestTimeoutMillis;
    private final long idleTimeoutMillis;
    private final RequestStats stats = new RequestStats();
    private final CachedDistribution queuedRequestsPerDestination;
    private final CachedDistribution activeConnectionsPerDestination;
    private final CachedDistribution idleConnectionsPerDestination;
    private final List<HttpRequestFilter> requestFilters;
    private final Exception creationLocation = new Exception();
    private final String name;

    public JettyHttpClient()
    {
        this(new HttpClientConfig(), ImmutableList.<HttpRequestFilter>of());
    }

    public JettyHttpClient(HttpClientConfig config)
    {
        this(config, ImmutableList.<HttpRequestFilter>of());
    }

    public JettyHttpClient(HttpClientConfig config, Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this(config, Optional.<JettyIoPool>absent(), requestFilters);
    }

    public JettyHttpClient(HttpClientConfig config, JettyIoPool jettyIoPool, Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this(config, Optional.of(jettyIoPool), requestFilters);
    }

    private JettyHttpClient(HttpClientConfig config, Optional<JettyIoPool> jettyIoPool, Iterable<? extends HttpRequestFilter> requestFilters)
    {
        checkNotNull(config, "config is null");
        checkNotNull(jettyIoPool, "jettyIoPool is null");
        checkNotNull(requestFilters, "requestFilters is null");

        maxContentLength = config.getMaxContentLength().toBytes();
        requestTimeoutMillis = config.getRequestTimeout().toMillis();
        idleTimeoutMillis = config.getIdleTimeout().toMillis();

        creationLocation.fillInStackTrace();

        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");
        if (config.getKeyStorePath() != null) {
            sslContextFactory.setKeyStorePath(config.getKeyStorePath());
            sslContextFactory.setKeyStorePassword(config.getKeyStorePassword());
        }

        httpClient = new HttpClient(sslContextFactory);
        httpClient.setMaxConnectionsPerDestination(config.getMaxConnectionsPerServer());
        httpClient.setMaxRequestsQueuedPerDestination(config.getMaxRequestsQueuedPerDestination());

        // disable cookies
        httpClient.setCookieStore(new HttpCookieStore.Empty());

        // timeouts
        httpClient.setIdleTimeout(idleTimeoutMillis);
        httpClient.setConnectTimeout(config.getConnectTimeout().toMillis());
        httpClient.setAddressResolutionTimeout(config.getConnectTimeout().toMillis());

        HostAndPort socksProxy = config.getSocksProxy();
        if (socksProxy != null) {
            httpClient.getProxyConfiguration().getProxies().add(new Socks4Proxy(socksProxy.getHostText(), socksProxy.getPortOrDefault(1080)));
        }

        JettyIoPool pool = jettyIoPool.orNull();
        if (pool == null) {
            pool = new JettyIoPool("anonymous" + nameCounter.incrementAndGet(), new JettyIoPoolConfig());
        }

        name = pool.getName();
        httpClient.setExecutor(pool.getExecutor());
        httpClient.setByteBufferPool(pool.setByteBufferPool());
        httpClient.setScheduler(pool.setScheduler());

        try {
            httpClient.start();

            // remove the GZIP encoding from the client
            // TODO: there should be a better way to to do this
            httpClient.getContentDecoderFactories().clear();
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw Throwables.propagate(e);
        }

        this.requestFilters = ImmutableList.copyOf(requestFilters);

        this.activeConnectionsPerDestination = new CachedDistribution(() -> {
            Distribution distribution = new Distribution();
            for (Destination destination : httpClient.getDestinations()) {
                PoolingHttpDestination<?> poolingHttpDestination = (PoolingHttpDestination<?>) destination;
                distribution.add(poolingHttpDestination.getConnectionPool().getActiveConnections().size());
            }
            return distribution;
        });

        this.idleConnectionsPerDestination = new CachedDistribution(() -> {
            Distribution distribution = new Distribution();
            for (Destination destination : httpClient.getDestinations()) {
                PoolingHttpDestination<?> poolingHttpDestination = (PoolingHttpDestination<?>) destination;
                distribution.add(poolingHttpDestination.getConnectionPool().getIdleConnections().size());
            }
            return distribution;
        });

        this.queuedRequestsPerDestination = new CachedDistribution(() -> {
            Distribution distribution = new Distribution();
            for (Destination destination : httpClient.getDestinations()) {
                PoolingHttpDestination<?> poolingHttpDestination = (PoolingHttpDestination<?>) destination;
                distribution.add(poolingHttpDestination.getHttpExchanges().size());
            }
            return distribution;
        });
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        long requestStart = System.nanoTime();

        // apply filters
        request = applyRequestFilters(request);

        // create jetty request and response listener
        HttpRequest jettyRequest = buildJettyRequest(request);
        InputStreamResponseListener listener = new InputStreamResponseListener(maxContentLength)
        {
            @Override
            public void onContent(Response response, ByteBuffer content)
            {
                // ignore empty blocks
                if (content.remaining() == 0) {
                    return;
                }
                super.onContent(response, content);
            }
        };

        // fire the request
        jettyRequest.send(listener);

        // wait for response to begin
        Response response;
        try {
            response = listener.get(httpClient.getIdleTimeout(), MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return responseHandler.handleException(request, e);
        }
        catch (TimeoutException e) {
            return responseHandler.handleException(request, e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                return responseHandler.handleException(request, (Exception) cause);
            }
            else {
                return responseHandler.handleException(request, new RuntimeException(cause));
            }
        }

        // process response
        long responseStart = System.nanoTime();

        JettyResponse jettyResponse = null;
        T value;
        try {
            jettyResponse = new JettyResponse(response, listener.getInputStream());
            value = responseHandler.handle(request, jettyResponse);
        }
        finally {
            recordRequestComplete(stats, request, requestStart, jettyResponse, responseStart);
        }
        return value;
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        checkNotNull(request, "request is null");
        checkNotNull(responseHandler, "responseHandler is null");

        request = applyRequestFilters(request);

        HttpRequest jettyRequest = buildJettyRequest(request);

        JettyResponseFuture<T, E> future = new JettyResponseFuture<>(request, jettyRequest, responseHandler, stats);

        BufferingResponseListener listener = new BufferingResponseListener(future, Ints.saturatedCast(maxContentLength));

        try {
            jettyRequest.send(listener);
        }
        catch (RuntimeException e) {
            // normally this is a rejected execution exception because the client has been closed
            future.failed(e);
        }
        return future;
    }

    private Request applyRequestFilters(Request request)
    {
        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }
        return request;
    }

    private HttpRequest buildJettyRequest(Request finalRequest)
    {
        HttpRequest jettyRequest = (HttpRequest) httpClient.newRequest(finalRequest.getUri());

        // jetty client always adds the user agent header
        // todo should there be a default?
        jettyRequest.getHeaders().remove(HttpHeader.USER_AGENT);

        jettyRequest.method(finalRequest.getMethod());

        for (Entry<String, String> entry : finalRequest.getHeaders().entries()) {
            jettyRequest.header(entry.getKey(), entry.getValue());
        }

        BodyGenerator bodyGenerator = finalRequest.getBodyGenerator();
        if (bodyGenerator != null) {
            if (bodyGenerator instanceof StaticBodyGenerator) {
                StaticBodyGenerator staticBodyGenerator = (StaticBodyGenerator) bodyGenerator;
                jettyRequest.content(new BytesContentProvider(staticBodyGenerator.getBody()));
            }
            else {
                jettyRequest.content(new BodyGeneratorContentProvider(bodyGenerator, httpClient.getExecutor()));
            }
        }

        // timeouts
        jettyRequest.timeout(requestTimeoutMillis, MILLISECONDS);
        jettyRequest.idleTimeout(idleTimeoutMillis, MILLISECONDS);

        return jettyRequest;
    }

    public List<HttpRequestFilter> getRequestFilters()
    {
        return requestFilters;
    }

    @Override
    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    @Managed
    @Nested
    public CachedDistribution getActiveConnectionsPerDestination()
    {
        return activeConnectionsPerDestination;
    }

    @Managed
    @Nested
    public CachedDistribution getIdleConnectionsPerDestination()
    {
        return idleConnectionsPerDestination;
    }

    @Managed
    @Nested
    public CachedDistribution getQueuedRequestsPerDestination()
    {
        return queuedRequestsPerDestination;
    }

    @Managed
    public String dump()
    {
        return httpClient.dump();
    }

    @Managed
    public void dumpStdErr()
    {
        httpClient.dumpStdErr();
    }

    @Override
    public void close()
    {
        try {
            httpClient.stop();
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        catch (Exception ignored) {
        }
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .addValue(name)
                .toString();
    }

    @SuppressWarnings("UnusedDeclaration")
    public StackTraceElement[] getCreationLocation()
    {
        return creationLocation.getStackTrace();
    }

    private static class JettyResponse
            implements io.airlift.http.client.Response
    {
        private final Response response;
        private final CountingInputStream inputStream;

        public JettyResponse(Response response, InputStream inputStream)
        {
            this.response = response;
            this.inputStream = new CountingInputStream(inputStream);
        }

        @Override
        public int getStatusCode()
        {
            return response.getStatus();
        }

        @Override
        public String getStatusMessage()
        {
            return response.getReason();
        }

        @Override
        public String getHeader(String name)
        {
            return response.getHeaders().getStringField(name);
        }

        @Override
        public ListMultimap<String, String> getHeaders()
        {
            HttpFields headers = response.getHeaders();

            ImmutableListMultimap.Builder<String, String> builder = ImmutableListMultimap.builder();
            for (String name : headers.getFieldNamesCollection()) {
                for (String value : headers.getValuesList(name)) {
                    builder.put(name, value);
                }
            }
            return builder.build();
        }

        @Override
        public long getBytesRead()
        {
            return inputStream.getCount();
        }

        @Override
        public InputStream getInputStream()
        {
            return inputStream;
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("statusCode", getStatusCode())
                    .add("statusMessage", getStatusMessage())
                    .add("headers", getHeaders())
                    .toString();
        }
    }

    private static class JettyResponseFuture<T, E extends Exception>
            extends AbstractFuture<T>
            implements HttpResponseFuture<T>
    {
        public enum JettyAsyncHttpState
        {
            WAITING_FOR_CONNECTION,
            SENDING_REQUEST,
            WAITING_FOR_RESPONSE,
            PROCESSING_RESPONSE,
            DONE,
            FAILED,
            CANCELED
        }

        private static final Logger log = Logger.get(JettyResponseFuture.class);

        private final long requestStart = System.nanoTime();
        private final AtomicReference<JettyAsyncHttpState> state = new AtomicReference<>(JettyAsyncHttpState.WAITING_FOR_CONNECTION);
        private final Request request;
        private final org.eclipse.jetty.client.api.Request jettyRequest;
        private final ResponseHandler<T, E> responseHandler;
        private final RequestStats stats;

        public JettyResponseFuture(Request request, org.eclipse.jetty.client.api.Request jettyRequest, ResponseHandler<T, E> responseHandler, RequestStats stats)
        {
            this.request = request;
            this.jettyRequest = jettyRequest;
            this.responseHandler = responseHandler;
            this.stats = stats;
        }

        @Override
        public String getState()
        {
            return state.get().toString();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            state.set(JettyAsyncHttpState.CANCELED);
            jettyRequest.abort(new CancellationException());
            return super.cancel(mayInterruptIfRunning);
        }

        protected void completed(Response response, InputStream content)
        {
            if (state.get() == JettyAsyncHttpState.CANCELED) {
                return;
            }

            T value;
            try {
                value = processResponse(response, content);
            }
            catch (Throwable e) {
                // this will be an instance of E from the response handler or an Error
                storeException(e);
                return;
            }
            state.set(JettyAsyncHttpState.DONE);
            set(value);
        }

        private T processResponse(Response response, InputStream content)
                throws E
        {
            // this time will not include the data fetching portion of the response,
            // since the response is fully cached in memory at this point
            long responseStart = System.nanoTime();

            state.set(JettyAsyncHttpState.PROCESSING_RESPONSE);
            JettyResponse jettyResponse = null;
            T value;
            try {
                jettyResponse = new JettyResponse(response, content);
                value = responseHandler.handle(request, jettyResponse);
            }
            finally {
                recordRequestComplete(stats, request, requestStart, jettyResponse, responseStart);
            }
            return value;
        }

        protected void failed(Throwable throwable)
        {
            if (state.get() == JettyAsyncHttpState.CANCELED) {
                return;
            }

            // give handler a chance to rewrite the exception or return a value instead
            if (throwable instanceof Exception) {
                try {
                    T value = responseHandler.handleException(request, (Exception) throwable);
                    // handler returned a value, store it in the future
                    state.set(JettyAsyncHttpState.DONE);
                    set(value);
                    return;
                }
                catch (Throwable newThrowable) {
                    throwable = newThrowable;
                }
            }

            // at this point "throwable" will either be an instance of E
            // from the response handler or not an instance of Exception
            storeException(throwable);
        }

        private void storeException(Throwable throwable)
        {
            if (throwable instanceof CancellationException) {
                state.set(JettyAsyncHttpState.CANCELED);
            }
            else {
                state.set(JettyAsyncHttpState.FAILED);
            }
            if (throwable == null) {
                throwable = new Throwable("Throwable is null");
                log.error(throwable, "Something is broken");
            }

            setException(throwable);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("requestStart", requestStart)
                    .add("state", state)
                    .add("request", request)
                    .toString();
        }
    }

    private static void recordRequestComplete(RequestStats requestStats, Request request, long requestStart, JettyResponse response, long responseStart)
    {
        if (response == null) {
            return;
        }

        Duration responseProcessingTime = Duration.nanosSince(responseStart);
        Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);

        requestStats.record(request.getMethod(),
                response.getStatusCode(),
                response.getBytesRead(),
                response.getBytesRead(),
                requestProcessingTime,
                responseProcessingTime);
    }

    private static class BodyGeneratorContentProvider
            implements ContentProvider
    {
        private static final ByteBuffer DONE = ByteBuffer.allocate(0);
        private static final ByteBuffer EXCEPTION = ByteBuffer.allocate(0);

        private final BodyGenerator bodyGenerator;
        private final Executor executor;

        public BodyGeneratorContentProvider(BodyGenerator bodyGenerator, Executor executor)
        {
            this.bodyGenerator = bodyGenerator;
            this.executor = executor;
        }

        @Override
        public long getLength()
        {
            return -1;
        }

        @Override
        public Iterator<ByteBuffer> iterator()
        {
            final BlockingQueue<ByteBuffer> chunks = new ArrayBlockingQueue<>(16);
            final AtomicReference<Exception> exception = new AtomicReference<>();

            executor.execute(() -> {
                BodyGeneratorOutputStream out = new BodyGeneratorOutputStream(chunks);
                try {
                    bodyGenerator.write(out);
                    out.close();
                }
                catch (Exception e) {
                    exception.set(e);
                    chunks.add(EXCEPTION);
                }
            });

            return new AbstractIterator<ByteBuffer>()
            {
                @Override
                protected ByteBuffer computeNext()
                {
                    ByteBuffer chunk;
                    try {
                        chunk = chunks.take();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted", e);
                    }

                    if (chunk == EXCEPTION) {
                        throw Throwables.propagate(exception.get());
                    }
                    if (chunk == DONE) {
                        return endOfData();
                    }
                    return chunk;
                }
            };
        }

        private final class BodyGeneratorOutputStream
                extends OutputStream
        {
            private final BlockingQueue<ByteBuffer> chunks;

            private BodyGeneratorOutputStream(BlockingQueue<ByteBuffer> chunks)
            {
                this.chunks = chunks;
            }

            @Override
            public void write(int b)
                    throws IOException
            {
                try {
                    // must copy array since it could be reused
                    chunks.put(ByteBuffer.wrap(new byte[] {(byte) b}));
                }
                catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }

            @Override
            public void write(byte[] b, int off, int len)
                    throws IOException
            {
                try {
                    // must copy array since it could be reused
                    byte[] copy = Arrays.copyOfRange(b, off, len);
                    chunks.put(ByteBuffer.wrap(copy));
                }
                catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }

            @Override
            public void close()
                    throws IOException
            {
                try {
                    chunks.put(DONE);
                }
                catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }
        }
    }

    private static class BufferingResponseListener
            extends Listener.Adapter
    {
        private final JettyResponseFuture<?, ?> future;
        private final int maxLength;

        @GuardedBy("this")
        private byte[] buffer = new byte[(int) new DataSize(64, Unit.KILOBYTE).toBytes()];
        @GuardedBy("this")
        private int size;

        public BufferingResponseListener(JettyResponseFuture<?, ?> future, int maxLength)
        {
            this.future = checkNotNull(future, "future is null");
            Preconditions.checkArgument(maxLength > 0, "maxLength must be greater than zero");
            this.maxLength = maxLength;
        }

        @Override
        public synchronized void onHeaders(Response response)
        {
            long length = response.getHeaders().getLongField(HttpHeader.CONTENT_LENGTH.asString());
            if (length > maxLength) {
                response.abort(new ResponseTooLargeException());
            }
            if (length > buffer.length) {
                buffer = Arrays.copyOf(buffer, Ints.saturatedCast(length));
            }
        }

        @Override
        public synchronized void onContent(Response response, ByteBuffer content)
        {
            int length = content.remaining();
            int requiredCapacity = size + length;
            if (requiredCapacity > buffer.length) {
                if (requiredCapacity > maxLength) {
                    response.abort(new ResponseTooLargeException());
                    return;
                }

                // newCapacity = min(log2ceiling(requiredCapacity), maxLength);
                int newCapacity = min(Integer.highestOneBit(requiredCapacity) << 1, maxLength);

                buffer = Arrays.copyOf(buffer, newCapacity);
            }

            content.get(buffer, size, length);
            size += length;
        }

        @Override
        public synchronized void onComplete(Result result)
        {
            Throwable throwable = result.getFailure();
            if (throwable != null) {
                future.failed(throwable);
            }
            else {
                future.completed(result.getResponse(), new ByteArrayInputStream(buffer, 0, size));
            }
        }
    }

    /*
     * This class is needed because jmxutils only fetches a nested instance object once and holds on to it forever.
     * todo remove this when https://github.com/martint/jmxutils/issues/26 is implemented
     */
    @ThreadSafe
    public static class CachedDistribution
    {
        private final Supplier<Distribution> distributionSupplier;

        @GuardedBy("this")
        private Distribution distribution;
        @GuardedBy("this")
        private long lastUpdate = System.nanoTime();

        public CachedDistribution(Supplier<Distribution> distributionSupplier)
        {
            this.distributionSupplier = distributionSupplier;
        }

        public synchronized Distribution getDistribution()
        {
            // refresh stats only once a second
            if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastUpdate) > 1000) {
                this.distribution = distributionSupplier.get();
                this.lastUpdate = System.nanoTime();
            }
            return distribution;
        }

        @Managed
        public double getMaxError()
        {
            return getDistribution().getMaxError();
        }

        @Managed
        public double getCount()
        {
            return getDistribution().getCount();
        }

        @Managed
        public double getTotal()
        {
            return getDistribution().getTotal();
        }

        @Managed
        public long getP01()
        {
            return getDistribution().getP01();
        }

        @Managed
        public long getP05()
        {
            return getDistribution().getP05();
        }

        @Managed
        public long getP10()
        {
            return getDistribution().getP10();
        }

        @Managed
        public long getP25()
        {
            return getDistribution().getP25();
        }

        @Managed
        public long getP50()
        {
            return getDistribution().getP50();
        }

        @Managed
        public long getP75()
        {
            return getDistribution().getP75();
        }

        @Managed
        public long getP90()
        {
            return getDistribution().getP90();
        }

        @Managed
        public long getP95()
        {
            return getDistribution().getP95();
        }

        @Managed
        public long getP99()
        {
            return getDistribution().getP99();
        }

        @Managed
        public long getMin()
        {
            return getDistribution().getMin();
        }

        @Managed
        public long getMax()
        {
            return getDistribution().getMax();
        }

        @Managed
        public Map<Double, Long> getPercentiles()
        {
            return getDistribution().getPercentiles();
        }
    }
}
