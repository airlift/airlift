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
package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.testing.TestingResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.HeaderNames.RETRY_AFTER;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.SERVICE_UNAVAILABLE;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.ResponseHandlerUtils.captureUnexpectedResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUnexpectedResponseException
{
    private static final HeaderName REQUEST_ID = HeaderName.of("X-Request-Id");
    private static final HeaderName SET_COOKIE = HeaderName.of("Set-Cookie");

    @Test
    void testDiagnosticsAreRedacted()
    {
        Request request = prepareGet()
                .setUri(URI.create("https://user:password@example.test:8443/v1/items?api_key=secret#fragment"))
                .setHeader(AUTHORIZATION, "Bearer secret-token")
                .setHeader(REQUEST_ID, "request-secret")
                .build();
        TestingResponse response = new TestingResponse(
                INTERNAL_SERVER_ERROR,
                ImmutableListMultimap.of(SET_COOKIE, "session=secret"),
                "{\"error\":\"secret-response\"}".getBytes(UTF_8));

        UnexpectedResponseException exception = new UnexpectedResponseException("Request failed", request, response);

        assertThat(request.toString())
                .contains("https://example.test:8443/v1/items")
                .contains("authorization")
                .contains("x-request-id")
                .doesNotContain("user", "password", "api_key", "secret-token", "request-secret", "fragment");
        assertThat(exception.getMessage()).isEqualTo("Request failed");
        assertThat(exception.getHeader(SET_COOKIE)).isEqualTo("session=secret");
        assertThat(exception.toString())
                .contains("GET https://example.test:8443/v1/items")
                .contains("statusCode=500")
                .doesNotContain("secret", "Set-Cookie", "headers");
    }

    @Test
    void testFailureBodyIsBounded()
    {
        TestingResponse response = new TestingResponse(
                INTERNAL_SERVER_ERROR,
                ImmutableListMultimap.of(
                        CONTENT_TYPE, "application/json; charset=utf-8",
                        SET_COOKIE, "session=secret"),
                "{\"error\":\"secret-response\"}".getBytes(UTF_8));

        UnexpectedResponseException exception = captureUnexpectedResponse(
                "Request failed",
                prepareGet().setUri(URI.create("https://example.test/v1/items")).build(),
                response,
                10);

        assertThat(exception.getRequestMethod()).isEqualTo("GET");
        assertThat(exception.getStatusCode()).isEqualTo(500);
        assertThatThrownBy(() -> exception.getHeaders().put(CONTENT_TYPE, "text/plain"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(exception.getContentType()).contains("application/json; charset=utf-8");
        assertThat(exception.getResponseBody()).isEqualTo("{\"error\":\"");
        assertThat(exception.getResponseBodyBytes()).containsExactly("{\"error\":\"".getBytes(UTF_8));
        assertThat(exception.isResponseBodyTruncated()).isTrue();
        assertThat(exception.toString()).doesNotContain("secret", "application/json", "responseBody");
    }

    @Test
    void testInputStreamFailureBodyIsReadOnlyToBound()
    {
        TestingResponse response = new TestingResponse(
                INTERNAL_SERVER_ERROR,
                ImmutableListMultimap.of(CONTENT_TYPE, "text/plain; charset=utf-8"),
                new ByteArrayInputStream("bounded-body".getBytes(UTF_8)));

        UnexpectedResponseException exception = captureUnexpectedResponse(
                "Request failed",
                null,
                response,
                7);

        assertThat(exception.getResponseBody()).isEqualTo("bounded");
        assertThat(exception.isResponseBodyTruncated()).isTrue();
        assertThat(response.getBytesRead()).isEqualTo(8);
    }

    @Test
    void testFailureBodyReadErrorRetainsStructuredResponse()
    {
        TestingResponse response = new TestingResponse(
                INTERNAL_SERVER_ERROR,
                ImmutableListMultimap.of(CONTENT_TYPE, "text/plain"),
                new InputStream()
                {
                    @Override
                    public int read()
                            throws IOException
                    {
                        throw new IOException("read failed");
                    }
                });

        UnexpectedResponseException exception = captureUnexpectedResponse("Request failed", null, response);

        assertThat(exception.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR.code());
        assertThat(exception.getContentType()).contains("text/plain");
        assertThat(exception.getResponseBodyBytes()).isEmpty();
        // nothing was read, so nothing was truncated; the read failure is the cause
        assertThat(exception.isResponseBodyTruncated()).isFalse();
        assertThat(exception).hasCauseInstanceOf(UncheckedIOException.class);
    }

    @Test
    void testSanitizedUriPreservesRawPath()
    {
        Request request = prepareGet()
                .setUri(URI.create("https://user:password@example.test/a%2Fb/%E2%82%AC?token=secret#fragment"))
                .build();

        UnexpectedResponseException exception = new UnexpectedResponseException(
                "Request failed",
                request,
                INTERNAL_SERVER_ERROR.code(),
                ImmutableListMultimap.of());

        assertThat(exception.getRequestUri()).isEqualTo(URI.create("https://example.test/a%2Fb/%E2%82%AC"));
        assertThat(exception.toString()).contains("GET https://example.test/a%2Fb/%E2%82%AC");
    }

    @Test
    void testRetryAfter()
    {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(retryAfter("120").getRetryAfter(now)).contains(Duration.ofMinutes(2));
        assertThat(retryAfter(" 7 ").getRetryAfter(now)).contains(Duration.ofSeconds(7));
        assertThat(retryAfter("Thu, 01 Jan 2026 00:00:30 GMT").getRetryAfter(now)).contains(Duration.ofSeconds(30));
        // a date in the past means retry now
        assertThat(retryAfter("Wed, 31 Dec 2025 23:59:00 GMT").getRetryAfter(now)).contains(Duration.ZERO);
        // delay-seconds past what a duration in milliseconds can hold saturates
        assertThat(retryAfter(String.valueOf(Long.MAX_VALUE)).getRetryAfter(now)).contains(Duration.ofMillis(Long.MAX_VALUE));
        assertThat(retryAfter("-1").getRetryAfter(now)).isEmpty();
        assertThat(retryAfter(String.valueOf(Long.MIN_VALUE)).getRetryAfter(now)).isEmpty();
        assertThat(retryAfter("soon").getRetryAfter(now)).isEmpty();
        assertThat(UnexpectedResponseException.builder("Unavailable", SERVICE_UNAVAILABLE.code()).build().getRetryAfter(now)).isEmpty();
    }

    @Test
    void testBuilder()
    {
        IllegalStateException cause = new IllegalStateException("read failed");
        UnexpectedResponseException exception = UnexpectedResponseException.builder("Request failed", INTERNAL_SERVER_ERROR.code())
                .setRequest(prepareGet().setUri(URI.create("https://example.test/v1/items?token=secret")).build())
                .setHeaders(ImmutableListMultimap.of(CONTENT_TYPE, "text/plain; charset=iso-8859-1"))
                .setResponseBody(new byte[] {(byte) 0xE9}, true)
                .setCause(cause)
                .build();

        assertThat(exception.getMessage()).isEqualTo("Request failed");
        assertThat(exception.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR.code());
        assertThat(exception.getRequestMethod()).isEqualTo("GET");
        assertThat(exception.getRequestUri()).isEqualTo(URI.create("https://example.test/v1/items"));
        assertThat(exception.getResponseBody()).isEqualTo("\u00e9");
        assertThat(exception.isResponseBodyTruncated()).isTrue();
        assertThat(exception).hasCause(cause);
    }

    @Test
    void testConstructorLeavesCauseUnset()
    {
        UnexpectedResponseException exception = new UnexpectedResponseException("Request failed", null, INTERNAL_SERVER_ERROR.code(), ImmutableListMultimap.of());

        // existing callers may still attach a cause after construction
        IllegalStateException cause = new IllegalStateException("late");
        exception.initCause(cause);
        assertThat(exception).hasCause(cause);
        assertThat(exception.getResponseBodyBytes()).isEmpty();
        assertThat(exception.isResponseBodyTruncated()).isFalse();
    }

    private static UnexpectedResponseException retryAfter(String value)
    {
        return UnexpectedResponseException.builder("Unavailable", SERVICE_UNAVAILABLE.code())
                .setHeaders(ImmutableListMultimap.of(RETRY_AFTER, value))
                .build();
    }
}
