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
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.util.Fields;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.airlift.http.client.TraceTokenRequestFilter.TRACETOKEN_HEADER;
import static io.airlift.http.client.jetty.HttpRequestEvent.NO_RESPONSE;
import static io.airlift.http.client.jetty.HttpRequestEvent.getFailureReason;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jetty.http.HttpVersion.HTTP_1_1;
import static org.eclipse.jetty.http.HttpVersion.HTTP_2;
import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class TestHttpClientLogger
{
    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    private File file;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        file = File.createTempFile(getClass().getName(), ".log");
    }

    @AfterMethod(alwaysRun = true)
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

        HttpFields headers = new HttpFields();
        headers.add(TRACETOKEN_HEADER, "test-token");

        Request request = new TestRequest(HTTP_2, method, uri, headers);
        Response response = new TestResponse(status);

        DefaultHttpClientLogger logger = new DefaultHttpClientLogger(
                file.getAbsolutePath(),
                1,
                256,
                new DataSize(1, MEGABYTE),
                Long.MAX_VALUE,
                false);
        RequestInfo requestInfo = RequestInfo.from(request, requestTimestamp);
        ResponseInfo responseInfo = ResponseInfo.from(Optional.of(response), responseSize);
        logger.log(requestInfo, responseInfo);
        logger.close();

        String actual = Files.toString(file, UTF_8);
        String[] columns = actual.trim().split("\\t");
        assertEquals(columns[0], ISO_FORMATTER.format(Instant.ofEpochMilli(requestTimestamp)));
        assertEquals(columns[1], HTTP_2.toString());
        assertEquals(columns[2], method);
        assertEquals(columns[3], uri.toString());
        assertEquals(columns[4], Integer.toString(status));
        assertEquals(columns[5], Long.toString(responseSize));
        assertEquals(columns[6], Long.toString(responseInfo.getResponseTimestampMillis() - requestTimestamp));
        assertEquals(columns[7], "test-token");
    }

    @Test
    public void testClientLogNoResponse()
            throws Exception
    {
        String method = "GET";
        URI uri = new URI("http://www.google.com");
        long requestTimestamp = System.currentTimeMillis();

        HttpFields headers = new HttpFields();
        headers.add(TRACETOKEN_HEADER, "test-token");
        Request request = new TestRequest(HTTP_1_1, method, uri, headers);

        DefaultHttpClientLogger logger = new DefaultHttpClientLogger(
                file.getAbsolutePath(),
                1,
                256,
                new DataSize(1, MEGABYTE),
                Long.MAX_VALUE,
                false);
        RequestInfo requestInfo = RequestInfo.from(request, requestTimestamp);
        ResponseInfo responseInfo = ResponseInfo.failed(Optional.empty(), Optional.of(new TimeoutException("timeout")));
        logger.log(requestInfo, responseInfo);
        logger.close();

        String actual = Files.toString(file, UTF_8);
        String[] columns = actual.trim().split("\\t");
        assertEquals(columns[0], ISO_FORMATTER.format(Instant.ofEpochMilli(requestTimestamp)));
        assertEquals(columns[1], HTTP_1_1.toString());
        assertEquals(columns[2], method);
        assertEquals(columns[3], uri.toString());
        assertEquals(columns[4], getFailureReason(responseInfo).get());
        assertEquals(columns[5], Integer.toString(NO_RESPONSE));
        assertEquals(columns[6], Long.toString(responseInfo.getResponseTimestampMillis() - requestTimestamp));
        assertEquals(columns[7], "test-token");
    }

    private class TestRequest
            implements Request
    {
        private final HttpVersion protocolVersion;
        private final String method;
        private final URI uri;
        @Nullable
        private final HttpFields headers;

        public TestRequest(HttpVersion protocolVersion, String method, URI uri, HttpFields headers)
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
        public Request header(String name, String value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request header(HttpHeader header, String value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<HttpCookie> getCookies()
        {
            return null;
        }

        @Override
        public Request cookie(HttpCookie cookie)
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
        public ContentProvider getContent()
        {
            return null;
        }

        @Override
        public Request content(ContentProvider content)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Request content(ContentProvider content, String contentType)
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
        public <T extends RequestListener> List<T> getRequestListeners(Class<T> listenerClass)
        {
            return null;
        }

        @Override
        public Request listener(Listener listener)
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
        public boolean abort(Throwable cause)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Throwable getAbortCause()
        {
            return null;
        }
    }

    private class TestResponse
            implements Response
    {
        private final int status;

        public TestResponse(int status)
        {
            this.status = status;
        }

        @Override
        public Request getRequest()
        {
            return null;
        }

        @Override
        public <T extends ResponseListener> List<T> getListeners(Class<T> listenerClass)
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
        public boolean abort(Throwable cause)
        {
            throw new UnsupportedOperationException();
        }
    }
}
