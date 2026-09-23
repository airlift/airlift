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
import io.airlift.http.client.testing.TestingHttpClient;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.HeaderNames.RETRY_AFTER;
import static io.airlift.http.client.HttpStatus.BAD_REQUEST;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.HttpStatus.SERVICE_UNAVAILABLE;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestClientCredentialsTokenProvider
{
    private static final URI TOKEN_URI = URI.create("https://auth.example.test/oauth/token");
    private static final Duration NO_DELAY = new Duration(0, TimeUnit.MILLISECONDS);

    @Test
    void testClientSecretBasicFormRequestAndCache()
    {
        AtomicInteger requests = new AtomicInteger();
        try (TestingHttpClient httpClient = new TestingHttpClient(request -> {
            requests.incrementAndGet();
            assertThat(request.getUri()).isEqualTo(TOKEN_URI);
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getHeader(AUTHORIZATION)).isEqualTo("Basic Y2xpZW50K2lkJTNBJTJCOnMlQzMlQUJjcmV0KyUyQiUzQQ==");
            assertThat(request.getHeader(CONTENT_TYPE)).isEqualTo("application/x-www-form-urlencoded");
            assertThat(new String(((StaticBodyGenerator) request.getBodyGenerator()).getBody(), UTF_8))
                    .isEqualTo("grant_type=client_credentials&scope=read+write&resource=https%3A%2F%2Fresource.example.test%2Fcatalog%3Fa%3Db");
            return mockResponse(OK, JSON_UTF_8,
                    """
                    {"access_token":"access-token","token_type":"Bearer","expires_in":300,"scope":"read write"}
                    """);
        })) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(
                            httpClient,
                            TOKEN_URI,
                            "client id:+",
                            "sëcret +:")
                    .scopes(List.of("read", "write"))
                    .resource("https://resource.example.test/catalog?a=b")
                    .build();

            assertThat(provider.getToken()).isEqualTo("access-token");
            assertThat(provider.getToken()).isEqualTo("access-token");
            assertThat(provider.getGrantedScopes()).containsExactlyInAnyOrder("read", "write");
            assertThat(requests).hasValue(1);
            assertThat(provider.toString()).isEqualTo("ClientCredentialsTokenProvider{REDACTED}");
        }
    }

    @Test
    void testRejectsExplicitlyInsufficientScopes()
    {
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> mockResponse(OK, JSON_UTF_8,
                """
                {"access_token":"access-token","token_type":"Bearer","expires_in":300,"scope":"read"}
                """))) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .scopes(List.of("read", "write"))
                    .build();

            // a scope mismatch is a malformed token document like any other and uses the same structured failure
            assertThatThrownBy(provider::getToken)
                    .isInstanceOfSatisfying(OAuth2TokenException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(200);
                        assertThat(exception.getMessage()).isEqualTo("OAuth2 token endpoint returned an invalid successful response: scope does not include the requested scopes");
                    });
        }
    }

    @Test
    void testOmittedScopeMeansRequestedScopesWereGranted()
    {
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> mockResponse(OK, JSON_UTF_8,
                """
                {"access_token":"access-token","token_type":"Bearer","expires_in":300}
                """))) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .scopes(List.of("read", "write"))
                    .build();

            assertThat(provider.getToken()).isEqualTo("access-token");
            assertThat(provider.getGrantedScopes()).containsExactlyInAnyOrder("read", "write");
        }
    }

    @Test
    void testRefreshesAtSkewAndReusesUnexpiredTokenAfterFailedProactiveRefresh()
    {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AtomicInteger requests = new AtomicInteger();
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> {
            if (requests.incrementAndGet() == 1) {
                return mockResponse(OK, JSON_UTF_8,
                        """
                        {"access_token":"first-token","token_type":"bearer","expires_in":120}
                        """);
            }
            throw new IOException("token endpoint unavailable");
        })) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .maxAttempts(1)
                    .clock(clock)
                    .build();

            assertThat(provider.getToken()).isEqualTo("first-token");
            clock.advanceSeconds(91);
            assertThat(provider.getToken()).isEqualTo("first-token");
            assertThat(requests).hasValue(2);
        }
    }

    @Test
    void testSuppressesConcurrentRefreshAndReusesAnotherCallersToken()
            throws Exception
    {
        AtomicInteger requests = new AtomicInteger();
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> {
            requests.incrementAndGet();
            return mockResponse(OK, JSON_UTF_8,
                    """
                    {"access_token":"shared-token","token_type":"Bearer","expires_in":300}
                    """);
        });
                var executor = Executors.newFixedThreadPool(8)) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret").build();
            List<Callable<String>> calls = java.util.stream.IntStream.range(0, 32)
                    .mapToObj(_ -> (Callable<String>) provider::getToken)
                    .toList();

            for (var future : executor.invokeAll(calls)) {
                assertThat(future.get()).isEqualTo("shared-token");
            }
            assertThat(requests).hasValue(1);

            assertThat(provider.refreshToken("already-replaced-token")).isTrue();
            assertThat(requests).hasValue(1);
        }
    }

    @Test
    void testRejectedTokenIsNeverReused()
    {
        AtomicInteger requests = new AtomicInteger();
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> {
            requests.incrementAndGet();
            return mockResponse(OK, JSON_UTF_8,
                    """
                    {"access_token":"same-token","token_type":"Bearer","expires_in":300}
                    """);
        })) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret").build();

            assertThat(provider.getToken()).isEqualTo("same-token");
            assertThat(provider.refreshToken("same-token")).isFalse();
            assertThat(requests).hasValue(2);
        }
    }

    @Test
    void testHugeExpiryClampsToInstantMax()
    {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AtomicInteger requests = new AtomicInteger();
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> {
            requests.incrementAndGet();
            return mockResponse(OK, JSON_UTF_8,
                    """
                    {"access_token":"forever-token","token_type":"Bearer","expires_in":40000000000000000}
                    """);
        })) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .clock(clock)
                    .build();

            // past Instant.MAX the expiry clamps instead of failing, and the token stays cached
            assertThat(provider.getToken()).isEqualTo("forever-token");
            clock.advanceSeconds(3_000_000_000L);
            assertThat(provider.getToken()).isEqualTo("forever-token");
            assertThat(requests).hasValue(1);
        }
    }

    @Test
    void testDatedRetryAfterIsHonored()
    {
        // a dated Retry-After is honored up to the configured maximum delay, like delay-seconds
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(now);
        AtomicInteger requests = new AtomicInteger();
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> {
            if (requests.incrementAndGet() < 2) {
                return new TestingResponse(
                        SERVICE_UNAVAILABLE,
                        ImmutableListMultimap.of(
                                CONTENT_TYPE, JSON_UTF_8.toString(),
                                RETRY_AFTER, "Thu, 01 Jan 2026 01:00:00 GMT"),
                        "{\"error\":\"temporarily_unavailable\"}".getBytes(UTF_8));
            }
            return mockResponse(OK, JSON_UTF_8,
                    """
                    {"access_token":"recovered-token","token_type":"Bearer","expires_in":300}
                    """);
        })) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .retryInitialDelay(NO_DELAY)
                    .retryMaxDelay(NO_DELAY)
                    .clock(clock)
                    .build();

            assertThat(provider.getToken()).isEqualTo("recovered-token");
            assertThat(requests).hasValue(2);
        }
    }

    @Test
    void testInvalidFieldIsNamedWithoutQuotingTheDocument()
    {
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> mockResponse(OK, JSON_UTF_8,
                """
                {"access_token":"secret-access-token","token_type":"Bearer","expires_in":"secret-expiry"}
                """))) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .retryInitialDelay(NO_DELAY)
                    .retryMaxDelay(NO_DELAY)
                    .build();

            assertThatThrownBy(provider::getToken)
                    .isInstanceOfSatisfying(OAuth2TokenException.class, exception -> {
                        assertThat(exception.getMessage()).isEqualTo("OAuth2 token endpoint returned an invalid successful response: expires_in has an invalid value");
                        assertThat(exception.getCause()).isNull();
                        assertThat(exception.getResponseBody()).contains("secret-expiry");
                    });
        }
    }

    @Test
    @Timeout(10)
    void testZeroRetryAfterRetriesImmediately()
    {
        AtomicInteger requests = new AtomicInteger();
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> {
            if (requests.incrementAndGet() < 2) {
                return new TestingResponse(
                        SERVICE_UNAVAILABLE,
                        ImmutableListMultimap.of(
                                CONTENT_TYPE, JSON_UTF_8.toString(),
                                RETRY_AFTER, "0"),
                        "{\"error\":\"temporarily_unavailable\"}".getBytes(UTF_8));
            }
            return mockResponse(OK, JSON_UTF_8,
                    """
                    {"access_token":"recovered-token","token_type":"Bearer","expires_in":300}
                    """);
        })) {
            // without Retry-After the first retry would wait the full minute
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .retryInitialDelay(new Duration(1, TimeUnit.MINUTES))
                    .retryMaxDelay(new Duration(1, TimeUnit.MINUTES))
                    .build();

            assertThat(provider.getToken()).isEqualTo("recovered-token");
            assertThat(requests).hasValue(2);
        }
    }

    @Test
    void testRetriesTokenAcquisitionExactlyThreeTimes()
    {
        AtomicInteger requests = new AtomicInteger();
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> {
            if (requests.incrementAndGet() < 3) {
                return new TestingResponse(
                        SERVICE_UNAVAILABLE,
                        ImmutableListMultimap.of(
                                CONTENT_TYPE, JSON_UTF_8.toString(),
                                RETRY_AFTER, "999999"),
                        "{\"error\":\"temporarily_unavailable\"}".getBytes(UTF_8));
            }
            return mockResponse(OK, JSON_UTF_8,
                    """
                    {"access_token":"recovered-token","token_type":"Bearer","expires_in":300}
                    """);
        })) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .retryInitialDelay(NO_DELAY)
                    .retryMaxDelay(NO_DELAY)
                    .build();

            assertThat(provider.getToken()).isEqualTo("recovered-token");
            assertThat(requests).hasValue(3);
        }
    }

    @Test
    void testLargeTokenDocumentIsParsedCompletely()
    {
        String token = "t".repeat(UnexpectedResponseException.DEFAULT_MAX_RESPONSE_BODY_BYTES);
        String document = "{\"extension\":\"" + "x".repeat(UnexpectedResponseException.DEFAULT_MAX_RESPONSE_BODY_BYTES) + "\",\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\"}";
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> new TestingResponse(
                OK,
                ImmutableListMultimap.of(CONTENT_TYPE, JSON_UTF_8.toString()),
                document.getBytes(UTF_8)))) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .retryInitialDelay(NO_DELAY)
                    .retryMaxDelay(NO_DELAY)
                    .build();

            assertThat(provider.getToken()).isEqualTo(token);
        }
    }

    @Test
    void testMalformedTokenDocumentDoesNotLeakThroughCause()
    {
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> mockResponse(OK, JSON_UTF_8, "{\"access_token\": secret-not-json}"))) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .retryInitialDelay(NO_DELAY)
                    .retryMaxDelay(NO_DELAY)
                    .build();

            assertThatThrownBy(provider::getToken)
                    .isInstanceOfSatisfying(OAuth2TokenException.class, exception -> {
                        assertThat(exception.getMessage()).isEqualTo("OAuth2 token endpoint returned an invalid successful response: response is not a valid JSON object");
                        assertThat(exception.getCause()).isNull();
                    });
        }
    }

    @Test
    void testMalformedContentTypeIsStructuredFailure()
    {
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> new TestingResponse(
                OK,
                ImmutableListMultimap.of(CONTENT_TYPE, "not a media type"),
                "{\"access_token\":\"token\",\"token_type\":\"Bearer\"}".getBytes(UTF_8)))) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .retryInitialDelay(NO_DELAY)
                    .retryMaxDelay(NO_DELAY)
                    .build();

            assertThatThrownBy(provider::getToken)
                    .isInstanceOfSatisfying(OAuth2TokenException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(200);
                        assertThat(exception.getMessage()).isEqualTo("OAuth2 token endpoint returned an invalid successful response: Content-Type is not application/json");
                    });
        }

        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> new TestingResponse(
                BAD_REQUEST,
                ImmutableListMultimap.of(CONTENT_TYPE, "not a media type"),
                "{\"error\":\"invalid_client\"}".getBytes(UTF_8)))) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, TOKEN_URI, "client", "secret")
                    .retryInitialDelay(NO_DELAY)
                    .retryMaxDelay(NO_DELAY)
                    .build();

            assertThatThrownBy(provider::getToken)
                    .isInstanceOfSatisfying(OAuth2TokenException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(400);
                        assertThat(exception.getError()).isEmpty();
                        assertThat(exception.getResponseBody()).isEqualTo("{\"error\":\"invalid_client\"}");
                    });
        }
    }

    @Test
    void testStructuredOAuthErrorIsRedactedAndNotRetried()
    {
        AtomicInteger requests = new AtomicInteger();
        URI tokenUriWithQuery = URI.create("https://auth.example.test/oauth/token?tenant=secret-tenant");
        try (TestingHttpClient httpClient = new TestingHttpClient(_ -> {
            requests.incrementAndGet();
            return new TestingResponse(
                    BAD_REQUEST,
                    ImmutableListMultimap.of(
                            CONTENT_TYPE, JSON_UTF_8.toString(),
                            HeaderName.of("X-Secret"), "response-header-secret"),
                    """
                    {"error":"invalid_client","error_description":"invalid-client-secret","error_uri":"https://errors.example.test/invalid-client"}
                    """.getBytes(UTF_8));
        })) {
            ClientCredentialsTokenProvider provider = ClientCredentialsTokenProvider.builder(httpClient, tokenUriWithQuery, "client", "configured-client-secret")
                    .retryInitialDelay(NO_DELAY)
                    .retryMaxDelay(NO_DELAY)
                    .build();

            assertThatThrownBy(provider::getToken)
                    .isInstanceOfSatisfying(OAuth2TokenException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(400);
                        assertThat(exception.getRequestUri()).isEqualTo(URI.create("https://auth.example.test/oauth/token"));
                        assertThat(exception.getError()).contains("invalid_client");
                        assertThat(exception.getErrorDescription()).contains("invalid-client-secret");
                        assertThat(exception.getErrorUri()).contains(URI.create("https://errors.example.test/invalid-client"));
                        assertThat(exception.getResponseBody()).contains("invalid-client-secret");
                        assertThat(exception.getMessage())
                                .doesNotContain("invalid-client-secret", "configured-client-secret", "response-header-secret", "secret-tenant");
                        assertThat(exception.toString())
                                .doesNotContain("invalid-client-secret", "configured-client-secret", "response-header-secret", "secret-tenant");
                    });
            assertThat(requests).hasValue(1);
        }
    }

    private static final class MutableClock
            extends Clock
    {
        private Instant instant;

        private MutableClock(Instant instant)
        {
            this.instant = instant;
        }

        public void advanceSeconds(long seconds)
        {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone()
        {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone)
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return instant;
        }
    }
}
