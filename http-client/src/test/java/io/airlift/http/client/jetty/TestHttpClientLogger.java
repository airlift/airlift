/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.client.jetty;

import com.google.common.io.Files;
import io.airlift.http.client.jetty.HttpClientLogger.RequestInfo;
import io.airlift.http.client.jetty.HttpClientLogger.ResponseInfo;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import jakarta.annotation.Nullable;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.util.Fields;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.airlift.http.client.jetty.HttpRequestEvent.NO_RESPONSE;
import static io.airlift.http.client.jetty.HttpRequestEvent.getFailureReason;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllLines;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpVersion.HTTP_1_1;
import static org.eclipse.jetty.http.HttpVersion.HTTP_2;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestHttpClientLogger
{
    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    private File file;

    @BeforeEach
    public void setup()
            throws IOException
    {
        file = File.createTempFile(getClass().getName(), ".log");
    }

    @AfterEach
    public void tearDown()
            throws IOException
    {
        if (file.exists() && !file.delete()) {
            throw new IOException("Error deleting " + file.getAbsolutePath());
        }
    }

    @Test
    public void testClientLog()
            throws Exception
    {
        String method = "GET";
        URI uri = new URI("http://www.google.com");
        int status = 200;
        long responseSize = 345;
        long requestTimestamp = System.currentTimeMillis();
        Request request = new TestRequest(HTTP_2, method, uri, HttpFields.build());
        Response response = new TestResponse(status);

        DefaultHttpClientLogger logger = new DefaultHttpClientLogger(
                file.getAbsolutePath(),
                1,
                256,
                DataSize.of(1, MEGABYTE),
                new Duration(10, SECONDS),
                Long.MAX_VALUE,
                false);

        long queueTime = MILLISECONDS.toNanos(1);
        long requestCreate = System.nanoTime();
        long requestBegin = requestCreate + queueTime;
        long requestEnd = requestCreate + MILLISECONDS.toNanos(3);
        long responseBegin = requestCreate + MILLISECONDS.toNanos(5);
        long responseComplete = requestCreate + MILLISECONDS.toNanos(7);
        long requestTotalTime = queueTime + (responseComplete - requestBegin);
        RequestInfo requestInfo = RequestInfo.from(request, requestTimestamp, requestCreate, requestBegin, requestEnd);
        ResponseInfo responseInfo = ResponseInfo.from(Optional.of(response), responseSize, responseBegin, responseComplete);
        logger.log(requestInfo, responseInfo);
        logger.close();

        String actual = Files.asCharSource(file, UTF_8).read();
        String[] columns = actual.trim().split("\\t");
        assertThat(columns[0]).isEqualTo(ISO_FORMATTER.format(Instant.ofEpochMilli(requestTimestamp)));
        assertThat(columns[1]).isEqualTo(HTTP_2.toString());
        assertThat(columns[2]).isEqualTo(method);
        assertThat(columns[3]).isEqualTo(uri.toString());
        assertThat(columns[4]).isEqualTo(Integer.toString(status));
        assertThat(columns[5]).isEqualTo(Long.toString(responseSize));
        assertThat(columns[6]).isEqualTo(Long.toString(NANOSECONDS.toMillis(requestTotalTime)));
        assertThat(columns[7]).isEqualTo(Long.toString(NANOSECONDS.toMillis(queueTime)));
        assertThat(columns[8]).isEqualTo(Long.toString(NANOSECONDS.toMillis(requestEnd - requestBegin)));
        assertThat(columns[9]).isEqualTo(Long.toString(NANOSECONDS.toMillis(responseBegin - requestEnd)));
        assertThat(columns[10]).isEqualTo(Long.toString(NANOSECONDS.toMillis(responseComplete - responseBegin)));
        assertThat(columns[11]).isEqualTo(Long.toString(responseInfo.getResponseTimestampMillis() - requestTimestamp));
    }

    @Test
    public void testClientLogNoResponse()
            throws Exception
    {
        String method = "GET";
        URI uri = new URI("http://www.google.com");
        long requestTimestamp = System.currentTimeMillis();
        Request request = new TestRequest(HTTP_1_1, method, uri, HttpFields.build());

        DefaultHttpClientLogger logger = new DefaultHttpClientLogger(
                file.getAbsolutePath(),
                1,
                256,
                DataSize.of(1, MEGABYTE),
                new Duration(10, SECONDS),
                Long.MAX_VALUE,
                false);
        RequestInfo requestInfo = RequestInfo.from(request, requestTimestamp);
        ResponseInfo responseInfo = ResponseInfo.failed(Optional.empty(), Optional.of(new TimeoutException("timeout")));
        logger.log(requestInfo, responseInfo);
        logger.close();

        String actual = Files.asCharSource(file, UTF_8).read();
        String[] columns = actual.trim().split("\\t");
        assertThat(columns[0]).isEqualTo(ISO_FORMATTER.format(Instant.ofEpochMilli(requestTimestamp)));
        assertThat(columns[1]).isEqualTo(HTTP_1_1.toString());
        assertThat(columns[2]).isEqualTo(method);
        assertThat(columns[3]).isEqualTo(uri.toString());
        assertThat(columns[4]).isEqualTo(getFailureReason(responseInfo).get());
        assertThat(columns[5]).isEqualTo(Integer.toString(NO_RESPONSE));
        assertThat(columns[6]).isNotEqualTo(Long.toString(0));
        assertThat(columns[7]).isEqualTo(Long.toString(0));
        assertThat(columns[8]).isEqualTo(Long.toString(0));
        assertThat(columns[9]).isEqualTo(Long.toString(0));
        assertThat(columns[10]).isEqualTo(Long.toString(0));
        assertThat(columns[11]).isEqualTo(Long.toString(responseInfo.getResponseTimestampMillis() - requestTimestamp));
    }

    @Test
    public void testClientLogPeriodicFlush()
            throws Exception
    {
        long now = System.currentTimeMillis();

        Request request = new TestRequest(HTTP_1_1, "GET", new URI("http://www.google.com"), HttpFields.from());

        DefaultHttpClientLogger logger = new DefaultHttpClientLogger(
                file.getAbsolutePath(),
                1,
                256,
                DataSize.of(1, MEGABYTE),
                new Duration(1, MILLISECONDS),
                Long.MAX_VALUE,
                false);

        RequestInfo requestInfo = RequestInfo.from(request, now);
        ResponseInfo responseInfo = ResponseInfo.from(Optional.empty(), 0, now, now);
        logger.log(requestInfo, responseInfo);

        // logging uses a background thread, so try a few times to make the test reliable
        for (int i = 0; i < 10; i++) {
            // wait for flush interval and for previous log entry to be processed
            Thread.sleep(20);

            // log a message which should force a flush
            logger.log(requestInfo, responseInfo);

            List<String> lines = readAllLines(file.toPath(), UTF_8);
            if (!lines.isEmpty()) {
                break;
            }
        }

        List<String> lines = readAllLines(file.toPath(), UTF_8);
        assertThat(lines).size().isGreaterThanOrEqualTo(1);
    }

    @SuppressWarnings("deprecation")
    private static class TestRequest
            implements Request
    {
        private final HttpVersion protocolVersion;
        private final String method;
        private final URI uri;
        @Nullable
        private final HttpFields headers;

        TestRequest(HttpVersion protocolVersion, String method, URI uri, HttpFields headers)
        {
            this.protocolVersion = requireNonNull(protocolVersion, "protocolVersion is null");
            this.method = requireNonNull(method, "method is null");
            this.uri = requireNonNull(uri, "uri is null");
            this.headers = headers;
        }

        @Override
        public String getScheme()
        {
            return null;
        }

        @Override
        public Request scheme(String scheme)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getHost()
        {
            return null;
        }

        @Override
        public int getPort()
        {
            return 0;
        }

        @Override
        public String getMethod()
        {
            return method;
        }

        @Override
        public Request method(HttpMethod method)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request method(String method)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPath()
        {
            return null;
        }

        @Override
        public Request path(String path)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getQuery()
        {
            return null;
        }

        @Override
        public URI getURI()
        {
            return uri;
        }

        @Override
        public HttpVersion getVersion()
        {
            return protocolVersion;
        }

        @Override
        public Request version(HttpVersion version)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Fields getParams()
        {
            return null;
        }

        @Override
        public Request param(String name, String value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpFields getHeaders()
        {
            return headers;
        }

        @Override
        public Request headers(Consumer<HttpFields.Mutable> consumer)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Supplier<HttpFields> getTrailersSupplier()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request trailersSupplier(Supplier<HttpFields> supplier)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<HttpCookie> getCookies()
        {
            return null;
        }

        @Override
        public Request cookie(HttpCookie httpCookie)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request tag(Object tag)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getTag()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request attribute(String name, Object value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> getAttributes()
        {
            return null;
        }

        @Override
        public Content getBody()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request body(Content content)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request file(Path file)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request file(Path file, String contentType)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAgent()
        {
            return null;
        }

        @Override
        public Request agent(String agent)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request accept(String... accepts)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getIdleTimeout()
        {
            return 0;
        }

        @Override
        public Request idleTimeout(long timeout, TimeUnit unit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getTimeout()
        {
            return 0;
        }

        @Override
        public Request timeout(long timeout, TimeUnit unit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isFollowRedirects()
        {
            return false;
        }

        @Override
        public Request followRedirects(boolean follow)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onRequestQueued(QueuedListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onRequestBegin(BeginListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onRequestHeaders(HeadersListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onRequestCommit(CommitListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onRequestContent(ContentListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onRequestSuccess(SuccessListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onRequestFailure(FailureListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onResponseBegin(Response.BeginListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onResponseHeader(Response.HeaderListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onResponseHeaders(Response.HeadersListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onResponseContent(Response.ContentListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onResponseContentAsync(Response.AsyncContentListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onResponseContentSource(Response.ContentSourceListener contentSourceListener)
        {
            return null;
        }

        @Override
        public Request onResponseSuccess(Response.SuccessListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onResponseFailure(Response.FailureListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request onPush(BiFunction<Request, Request, Response.CompleteListener> biFunction)
        {
            return null;
        }

        @Override
        public Request onComplete(Response.CompleteListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ContentResponse send()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(Response.CompleteListener listener)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Boolean> abort(Throwable cause)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Throwable getAbortCause()
        {
            return null;
        }
    }

    private static class TestResponse
            implements Response
    {
        private final int status;

        TestResponse(int status)
        {
            this.status = status;
        }

        @Override
        public Request getRequest()
        {
            return null;
        }

        @Override
        public HttpVersion getVersion()
        {
            return null;
        }

        @Override
        public int getStatus()
        {
            return status;
        }

        @Override
        public String getReason()
        {
            return null;
        }

        @Override
        public HttpFields getHeaders()
        {
            return null;
        }

        @Override
        public HttpFields getTrailers()
        {
            return null;
        }

        @Override
        public CompletableFuture<Boolean> abort(Throwable cause)
        {
            throw new UnsupportedOperationException();
        }
    }
}
