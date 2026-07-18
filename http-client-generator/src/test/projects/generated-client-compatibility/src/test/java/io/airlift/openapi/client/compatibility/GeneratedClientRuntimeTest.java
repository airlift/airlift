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
package io.airlift.openapi.client.compatibility;

import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import io.airlift.api.binding.ApiModule;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.BearerTokenProvider;
import io.airlift.http.client.ClientCredentialsTokenProvider;
import io.airlift.http.client.HttpClientCredentials;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.ServerSentEvent;
import io.airlift.http.client.ServerSentEventStream;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.client.testing.TestingHttpClient;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.openapi.client.generated.api.CompatibilityServiceClient;
import io.airlift.openapi.client.generated.api.NamedAuthenticationServiceClient;
import io.airlift.openapi.client.generated.ApiException;
import io.airlift.openapi.client.generated.ApiOAuth2;
import io.airlift.openapi.client.generated.RetryPolicy;
import io.airlift.openapi.client.generated.model.CompatibilityConflict;
import io.airlift.openapi.client.generated.model.CompatibilityEvent;
import io.airlift.openapi.client.generated.model.FrameKind;
import io.airlift.openapi.client.generated.model.NamedStatus;
import io.airlift.openapi.client.generated.model.QueryFrame;
import io.airlift.openapi.client.generated.model.ServiceStatus;
import io.airlift.openapi.client.generated.model.ServiceStatusPatch;
import io.airlift.openapi.client.generated.model.SqlFrame;
import io.airlift.openapi.client.generated.model.TextFrame;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.HeaderNames.RETRY_AFTER;
import static io.airlift.http.client.HeaderNames.WWW_AUTHENTICATE;
import static io.airlift.http.client.HttpStatus.CONFLICT;
import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static io.airlift.http.client.HttpStatus.NO_CONTENT;
import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.HttpStatus.SERVICE_UNAVAILABLE;
import static io.airlift.http.client.HttpStatus.UNAUTHORIZED;
import static io.airlift.http.client.testing.TestingResponse.mockResponse;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedClientRuntimeTest
{
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final URI TEST_URI = URI.create("http://example.test");
    private static final io.airlift.units.Duration NO_DELAY = new io.airlift.units.Duration(0, TimeUnit.MILLISECONDS);

    private LifeCycleManager lifeCycleManager;
    private JettyHttpClient httpClient;
    private URI baseUri;

    @BeforeAll
    void startServer()
    {
        Module apiModule = ApiModule.builder()
                .addApi(api -> api
                        .add(FrameService.class)
                        .add(StatusService.class))
                .build();

        Bootstrap app = new Bootstrap(
                new NodeModule(),
                new TestingHttpServerModule(getClass().getName()),
                new JsonModule(),
                new JaxrsModule(),
                apiModule,
                binder -> jaxrsBinder(binder).bind(BearerTokenFilter.class));
        Injector injector = app
                .setRequiredConfigurationProperty("node.environment", "test")
                .quiet()
                .doNotInitializeLogging()
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        HttpServerInfo serverInfo = injector.getInstance(HttpServerInfo.class);
        baseUri = UriBuilder.fromUri(serverInfo.getHttpUri())
                .host("localhost")
                .build();
        httpClient = new JettyHttpClient("generated-client-compatibility", new HttpClientConfig());
    }

    @AfterAll
    void stopServer()
            throws Exception
    {
        if (httpClient != null) {
            httpClient.close();
        }
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @Test
    void testAuthenticatedPolymorphicRoundTrip()
    {
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, accessToken(ACCESS_TOKEN));

        QueryFrame frame = client.getQueryFrame();

        assertThat(frame).isInstanceOf(TextFrame.class);
        TextFrame textFrame = (TextFrame) frame;
        assertThat(textFrame.query()).isEqualTo("status:error");
        assertThat(textFrame.kind()).isEqualTo(FrameKind.TEXT);
        assertThat(textFrame.acceptedKinds()).containsExactly(FrameKind.TEXT, FrameKind.SQL);
    }

    @Test
    void testGeneratedOneOfHierarchyIsClosed()
    {
        assertThat(QueryFrame.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(TextFrame.class, SqlFrame.class);
    }

    @Test
    void testTypedServerSentEventRoundTrip()
    {
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, accessToken(ACCESS_TOKEN));

        try (ServerSentEventStream<CompatibilityEvent> stream = client.streamEvents()) {
            assertThat(stream.readEvent()).contains(new ServerSentEvent<>(
                    Optional.of("status"),
                    Optional.of("status-1"),
                    new CompatibilityEvent("héllo", FrameKind.TEXT),
                    Optional.of(Duration.ofMillis(1500))));
            assertThat(stream.hasNext()).isFalse();
        }
    }

    @Test
    void testServerSentEventEstablishmentUsesStructuredFailure()
    {
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, accessToken("rejected-token"), RetryPolicy.disabled());

        assertThatThrownBy(client::streamEvents)
                .isInstanceOf(ApiException.class)
                .satisfies(throwable -> {
                    ApiException exception = (ApiException) throwable;
                    assertThat(exception.getOperationName()).isEqualTo("streamEvents");
                    assertThat(exception.getRequestMethod()).isEqualTo("POST");
                    assertThat(exception.getStatusCode()).isEqualTo(401);
                });
    }

    @Test
    void testServerSentEventContentTypeMismatchIsStructuredFailure()
    {
        AtomicBoolean closed = new AtomicBoolean();
        HttpClient streamingClient = streamingClient(new StubStreamingResponse(
                ImmutableListMultimap.of(CONTENT_TYPE, JSON_UTF_8.toString()),
                "{\"message\":\"not an event stream\"}".getBytes(UTF_8),
                closed));
        CompatibilityServiceClient client = new CompatibilityServiceClient(streamingClient, TEST_URI, accessToken(ACCESS_TOKEN), RetryPolicy.disabled());

        assertThatThrownBy(client::streamEvents)
                .isInstanceOf(ApiException.class)
                .satisfies(throwable -> {
                    ApiException exception = (ApiException) throwable;
                    assertThat(exception.getOperationName()).isEqualTo("streamEvents");
                    assertThat(exception.getStatusCode()).isEqualTo(200);
                    assertThat(exception.getCause()).hasMessageStartingWith("Unexpected content type");
                });
        assertThat(closed).isTrue();
    }

    @Test
    void testRefreshesDynamicBearerTokenAfterUnauthorizedResponse()
    {
        AtomicReference<String> token = new AtomicReference<>("rejected-token");
        AtomicInteger tokenResolutions = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        BearerTokenProvider tokenProvider = new BearerTokenProvider()
        {
            @Override
            public String getToken()
            {
                tokenResolutions.incrementAndGet();
                return token.get();
            }

            @Override
            public boolean refreshToken(String rejectedToken)
            {
                assertThat(rejectedToken).isEqualTo("rejected-token");
                refreshes.incrementAndGet();
                token.set(ACCESS_TOKEN);
                return true;
            }
        };
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, accessToken(tokenProvider));
        io.airlift.openapi.client.generated.second.api.CompatibilityServiceClient secondClient =
                new io.airlift.openapi.client.generated.second.api.CompatibilityServiceClient(httpClient, baseUri, accessToken(tokenProvider));

        QueryFrame frame = client.getQueryFrame();
        Object secondFrame = secondClient.getQueryFrame();

        assertThat(frame).isInstanceOf(TextFrame.class);
        assertThat(secondFrame).isNotNull();
        assertThat(tokenResolutions).hasValue(3);
        assertThat(refreshes).hasValue(1);
    }

    @Test
    void testDoesNotRetryWhenDynamicBearerTokenRefreshIsDeclined()
    {
        AtomicInteger tokenResolutions = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        BearerTokenProvider tokenProvider = new BearerTokenProvider()
        {
            @Override
            public String getToken()
            {
                tokenResolutions.incrementAndGet();
                return "rejected-token";
            }

            @Override
            public boolean refreshToken(String rejectedToken)
            {
                refreshes.incrementAndGet();
                return false;
            }
        };
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, accessToken(tokenProvider));

        assertThatThrownBy(client::getQueryFrame)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatusCode()).isEqualTo(401));
        assertThat(tokenResolutions).hasValue(1);
        assertThat(refreshes).hasValue(1);
    }

    @Test
    void testRetriesWhenRejectedBearerTokenWasConcurrentlyReplaced()
    {
        AtomicReference<String> token = new AtomicReference<>("rejected-token");
        AtomicInteger tokenResolutions = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        BearerTokenProvider tokenProvider = new BearerTokenProvider()
        {
            @Override
            public String getToken()
            {
                tokenResolutions.incrementAndGet();
                return token.getAndSet(ACCESS_TOKEN);
            }

            @Override
            public boolean refreshToken(String rejectedToken)
            {
                refreshes.incrementAndGet();
                return !token.get().equals(rejectedToken);
            }
        };
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, accessToken(tokenProvider));

        QueryFrame frame = client.getQueryFrame();

        assertThat(frame).isInstanceOf(TextFrame.class);
        assertThat(tokenResolutions).hasValue(2);
        assertThat(refreshes).hasValue(1);
    }

    @Test
    void testRetriesOnlyOnceWhenRefreshedBearerTokenIsUnauthorized()
    {
        AtomicReference<String> token = new AtomicReference<>("rejected-token");
        AtomicInteger tokenResolutions = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        BearerTokenProvider tokenProvider = new BearerTokenProvider()
        {
            @Override
            public String getToken()
            {
                tokenResolutions.incrementAndGet();
                return token.get();
            }

            @Override
            public boolean refreshToken(String rejectedToken)
            {
                refreshes.incrementAndGet();
                token.set("also-rejected-token");
                return true;
            }
        };
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, accessToken(tokenProvider));

        assertThatThrownBy(client::getQueryFrame)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatusCode()).isEqualTo(401));
        assertThat(tokenResolutions).hasValue(2);
        assertThat(refreshes).hasValue(1);
    }

    @Test
    void testDoesNotRefreshWithoutBearerChallenge()
    {
        for (List<String> challenges : List.of(
                List.<String>of(),
                List.of("Basic realm=\"test\""),
                List.of("NotBearer realm=\"test\""),
                List.of("BearerRealm=\"test\""),
                List.of("Bearer realm=\"unterminated"),
                List.of("Basic realm=\"test\", Bearer = \"parameter\""))) {
            AtomicInteger attempts = new AtomicInteger();
            AtomicInteger refreshes = new AtomicInteger();
            BearerTokenProvider tokenProvider = new BearerTokenProvider()
            {
                @Override
                public String getToken()
                {
                    return "rejected-token";
                }

                @Override
                public boolean refreshToken(String rejectedToken)
                {
                    refreshes.incrementAndGet();
                    return true;
                }
            };
            try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
                attempts.incrementAndGet();
                return unauthorizedResponse(challenges.toArray(String[]::new));
            })) {
                CompatibilityServiceClient client = new CompatibilityServiceClient(testingHttpClient, TEST_URI, accessToken(tokenProvider));

                assertThatThrownBy(client::getQueryFrame)
                        .as("WWW-Authenticate values: %s", challenges)
                        .isInstanceOf(ApiException.class)
                        .satisfies(exception -> assertThat(((ApiException) exception).getStatusCode()).isEqualTo(401));
                assertThat(attempts).as("WWW-Authenticate values: %s", challenges).hasValue(1);
                assertThat(refreshes).as("WWW-Authenticate values: %s", challenges).hasValue(0);
            }
        }
    }

    @Test
    void testMethodAwareTransportRetryAttempts()
    {
        AtomicInteger getAttempts = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            if (getAttempts.incrementAndGet() < 3) {
                return mockResponse(SERVICE_UNAVAILABLE, JSON_UTF_8, "{}");
            }
            return mockResponse(OK, JSON_UTF_8, "{\"defaultKind\":\"TEXT\"}");
        })) {
            CompatibilityServiceClient client = client(testingHttpClient, 2);
            assertThat(client.getServiceStatus()).isEqualTo(new ServiceStatus(FrameKind.TEXT));
            assertThat(getAttempts).hasValue(3);
        }

        AtomicInteger unsafePostAttempts = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            unsafePostAttempts.incrementAndGet();
            return mockResponse(SERVICE_UNAVAILABLE, JSON_UTF_8, "{}");
        })) {
            assertThatThrownBy(() -> client(testingHttpClient, 2).unsafeExecute())
                    .isInstanceOf(ApiException.class);
            assertThat(unsafePostAttempts).hasValue(1);
        }

        AtomicInteger blankKeyAttempts = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            blankKeyAttempts.incrementAndGet();
            return mockResponse(SERVICE_UNAVAILABLE, JSON_UTF_8, "{}");
        })) {
            assertThatThrownBy(() -> client(testingHttpClient, 2).safeExecute(" "))
                    .isInstanceOf(ApiException.class);
            assertThat(blankKeyAttempts).hasValue(1);
        }

        AtomicInteger idempotentPostAttempts = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(request -> {
            assertThat(request.getHeader(HeaderName.of("Idempotency-Key"))).isEqualTo("request-key");
            if (idempotentPostAttempts.incrementAndGet() < 3) {
                return mockResponse(SERVICE_UNAVAILABLE, JSON_UTF_8, "{}");
            }
            return mockResponse(NO_CONTENT, JSON_UTF_8, "");
        })) {
            client(testingHttpClient, 2).safeExecute("request-key");
            assertThat(idempotentPostAttempts).hasValue(3);
        }

        AtomicInteger patchAttempts = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            patchAttempts.incrementAndGet();
            return mockResponse(SERVICE_UNAVAILABLE, JSON_UTF_8, "{}");
        })) {
            assertThatThrownBy(() -> client(testingHttpClient, 2).patchStatus(new ServiceStatusPatch(FrameKind.SQL)))
                    .isInstanceOf(ApiException.class);
            assertThat(patchAttempts).hasValue(1);
        }

        AtomicInteger networkAttempts = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            if (networkAttempts.incrementAndGet() < 3) {
                throw new ConnectException("connection unavailable");
            }
            return mockResponse(OK, JSON_UTF_8, "{\"defaultKind\":\"TEXT\"}");
        })) {
            assertThat(client(testingHttpClient, 2).getServiceStatus())
                    .isEqualTo(new ServiceStatus(FrameKind.TEXT));
            assertThat(networkAttempts).hasValue(3);
        }

        AtomicInteger retryAfterAttempts = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            if (retryAfterAttempts.incrementAndGet() < 3) {
                return new io.airlift.http.client.testing.TestingResponse(
                        SERVICE_UNAVAILABLE,
                        ImmutableListMultimap.of(
                                CONTENT_TYPE, JSON_UTF_8.toString(),
                                RETRY_AFTER, Long.toString(Long.MAX_VALUE)),
                        "{}".getBytes(UTF_8));
            }
            return mockResponse(OK, JSON_UTF_8, "{\"defaultKind\":\"TEXT\"}");
        })) {
            assertThat(client(testingHttpClient, 2).getServiceStatus())
                    .isEqualTo(new ServiceStatus(FrameKind.TEXT));
            assertThat(retryAfterAttempts).hasValue(3);
        }
    }

    @Test
    void testRetryAfterHttpDateIsHonored()
    {
        // a dated Retry-After is honored like delay-seconds and capped at the configured maximum delay
        AtomicInteger attempts = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            if (attempts.incrementAndGet() < 2) {
                return new io.airlift.http.client.testing.TestingResponse(
                        SERVICE_UNAVAILABLE,
                        ImmutableListMultimap.of(
                                CONTENT_TYPE, JSON_UTF_8.toString(),
                                RETRY_AFTER, "Fri, 01 Jan 2100 00:00:00 GMT"),
                        "{}".getBytes(UTF_8));
            }
            return mockResponse(OK, JSON_UTF_8, "{\"defaultKind\":\"TEXT\"}");
        })) {
            assertThat(client(testingHttpClient, 2).getServiceStatus()).isEqualTo(new ServiceStatus(FrameKind.TEXT));
            assertThat(attempts).hasValue(2);
        }
    }

    @Test
    void testPostAuthenticationRefreshIsIndependentOfTransportRetries()
    {
        AtomicReference<String> token = new AtomicReference<>("rejected-token");
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        BearerTokenProvider provider = new BearerTokenProvider()
        {
            @Override
            public String getToken()
            {
                return token.get();
            }

            @Override
            public boolean refreshToken(String rejectedToken)
            {
                refreshes.incrementAndGet();
                token.set(ACCESS_TOKEN);
                return true;
            }
        };
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(request -> {
            attempts.incrementAndGet();
            if (request.getHeader(AUTHORIZATION).equals("Bearer " + ACCESS_TOKEN)) {
                return mockResponse(NO_CONTENT, JSON_UTF_8, "");
            }
            return unauthorizedResponse("Basic realm=\"test,internal\", Bearer realm=\"api\"");
        })) {
            CompatibilityServiceClient client = new CompatibilityServiceClient(testingHttpClient, TEST_URI, accessToken(provider), new RetryPolicy(2, NO_DELAY, NO_DELAY));
            client.unsafeExecute();
            assertThat(attempts).hasValue(2);
            assertThat(refreshes).hasValue(1);
        }

        AtomicInteger repeatedAttempts = new AtomicInteger();
        AtomicInteger repeatedRefreshes = new AtomicInteger();
        BearerTokenProvider repeatedProvider = new BearerTokenProvider()
        {
            @Override
            public String getToken()
            {
                return "rejected-" + repeatedRefreshes.get();
            }

            @Override
            public boolean refreshToken(String rejectedToken)
            {
                repeatedRefreshes.incrementAndGet();
                return true;
            }
        };
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            repeatedAttempts.incrementAndGet();
            return unauthorizedResponse("Bearer");
        })) {
            CompatibilityServiceClient client = new CompatibilityServiceClient(testingHttpClient, TEST_URI, accessToken(repeatedProvider), new RetryPolicy(2, NO_DELAY, NO_DELAY));
            assertThatThrownBy(client::unsafeExecute)
                    .isInstanceOf(ApiException.class)
                    .satisfies(exception -> assertThat(((ApiException) exception).getStatusCode()).isEqualTo(401));
            assertThat(repeatedAttempts).hasValue(2);
            assertThat(repeatedRefreshes).hasValue(1);
        }
    }

    @Test
    void testBearerTokenProviderFailuresAreStructuredFailures()
    {
        IllegalStateException tokenFailure = new IllegalStateException("token store unavailable");
        AtomicInteger requests = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            requests.incrementAndGet();
            return mockResponse(OK, JSON_UTF_8, "{\"defaultKind\":\"TEXT\"}");
        })) {
            BearerTokenProvider failingProvider = () -> {
                throw tokenFailure;
            };
            CompatibilityServiceClient client = new CompatibilityServiceClient(testingHttpClient, TEST_URI, accessToken(failingProvider), new RetryPolicy(2, NO_DELAY, NO_DELAY));
            assertThatThrownBy(client::getServiceStatus)
                    .isInstanceOf(ApiException.class)
                    .hasMessage("getServiceStatus call failed")
                    .hasCause(tokenFailure);
            assertThat(requests).hasValue(0);
        }

        IllegalStateException refreshFailure = new IllegalStateException("token refresh unavailable");
        AtomicInteger unauthorizedRequests = new AtomicInteger();
        BearerTokenProvider failingRefreshProvider = new BearerTokenProvider()
        {
            @Override
            public String getToken()
            {
                return "rejected-token";
            }

            @Override
            public boolean refreshToken(String rejectedToken)
            {
                assertThat(rejectedToken).isEqualTo("rejected-token");
                throw refreshFailure;
            }
        };
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            unauthorizedRequests.incrementAndGet();
            return unauthorizedResponse("Bearer");
        })) {
            CompatibilityServiceClient client = new CompatibilityServiceClient(testingHttpClient, TEST_URI, accessToken(failingRefreshProvider), new RetryPolicy(2, NO_DELAY, NO_DELAY));
            assertThatThrownBy(client::getServiceStatus)
                    .isInstanceOf(ApiException.class)
                    .hasMessage("getServiceStatus call failed")
                    .hasCause(refreshFailure);
            assertThat(unauthorizedRequests).hasValue(1);
        }
    }

    @Test
    void testNamedAuthenticationAlternativeSelection()
    {
        HttpClientCredentials combinedCredentials = HttpClientCredentials.builder()
                .basicAuth("serviceBasic", "client", "secret")
                .headerApiKey("serviceKey", "header-secret")
                .bearerToken("serviceBearer", BearerTokenProvider.fixedToken("unselected-token"))
                .build();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(request -> {
            assertThat(request.getHeader(AUTHORIZATION)).isEqualTo("Basic Y2xpZW50OnNlY3JldA==");
            assertThat(request.getHeader(HeaderName.of("X-Service-Key"))).isEqualTo("header-secret");
            return mockResponse(OK, JSON_UTF_8, "{\"value\":\"combined\"}");
        })) {
            NamedAuthenticationServiceClient client = new NamedAuthenticationServiceClient(testingHttpClient, TEST_URI, combinedCredentials);
            assertThat(client.combined()).isEqualTo(new NamedStatus("combined"));
        }

        HttpClientCredentials bearerCredentials = HttpClientCredentials.builder()
                .bearerToken("serviceBearer", BearerTokenProvider.fixedToken("selected-token"))
                .build();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(request -> {
            assertThat(request.getHeader(AUTHORIZATION)).isEqualTo("Bearer selected-token");
            assertThat(request.getHeader(HeaderName.of("X-Service-Key"))).isNull();
            return mockResponse(OK, JSON_UTF_8, "{\"value\":\"bearer\"}");
        })) {
            NamedAuthenticationServiceClient client = new NamedAuthenticationServiceClient(testingHttpClient, TEST_URI, bearerCredentials);
            assertThat(client.combined()).isEqualTo(new NamedStatus("bearer"));
        }

        try (TestingHttpClient testingHttpClient = new TestingHttpClient(request -> {
            assertThat(request.getHeader(AUTHORIZATION)).isNull();
            assertThat(request.getHeader(HeaderName.of("X-Service-Key"))).isNull();
            return mockResponse(OK, JSON_UTF_8, "{\"value\":\"public\"}");
        })) {
            NamedAuthenticationServiceClient client = new NamedAuthenticationServiceClient(testingHttpClient, TEST_URI, combinedCredentials);
            assertThat(client.publicOperation()).isEqualTo(new NamedStatus("public"));
        }

        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> {
            throw new AssertionError("request must not be sent without a satisfiable authentication alternative");
        })) {
            NamedAuthenticationServiceClient client = new NamedAuthenticationServiceClient(testingHttpClient, TEST_URI, HttpClientCredentials.none());
            assertThatThrownBy(client::combined)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No configured credentials satisfy authentication for operation 'combined'");
        }
    }

    @Test
    void testGeneratedOAuthFactorySharesProviderAcrossClientFamiliesAndRefreshesAfterUnauthorized()
    {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicInteger apiRequests = new AtomicInteger();
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(request -> {
            if (request.getUri().getPath().equals("/oauth/token")) {
                int requestNumber = tokenRequests.incrementAndGet();
                assertThat(request.getHeader(AUTHORIZATION)).isEqualTo("Basic Y2xpZW50OnNlY3JldA==");
                assertThat(request.getHeader(CONTENT_TYPE)).isEqualTo("application/x-www-form-urlencoded");
                assertThat(new String(((io.airlift.http.client.StaticBodyGenerator) request.getBodyGenerator()).getBody(), UTF_8))
                        .isEqualTo("grant_type=client_credentials&scope=read&resource=https%3A%2F%2Fresource.example.test%2Fcatalog");
                return mockResponse(OK, JSON_UTF_8, """
                        {"access_token":"token-%s","token_type":"Bearer","expires_in":300,"scope":"read"}
                        """.formatted(requestNumber));
            }

            int requestNumber = apiRequests.incrementAndGet();
            if (requestNumber == 1) {
                assertThat(request.getHeader(AUTHORIZATION)).isEqualTo("Bearer token-1");
                return unauthorizedResponse("bearer realm=\"api\"");
            }
            assertThat(request.getHeader(AUTHORIZATION)).isEqualTo("Bearer token-2");
            return mockResponse(OK, JSON_UTF_8, "{\"value\":\"oauth\"}");
        })) {
            ClientCredentialsTokenProvider provider = ApiOAuth2.createServiceOAuthTokenProvider(
                    testingHttpClient,
                    TEST_URI,
                    "client",
                    "secret",
                    "https://resource.example.test/catalog");
            HttpClientCredentials credentials = HttpClientCredentials.builder()
                    .bearerToken("serviceOAuth", provider)
                    .build();
            assertThat(credentials.bearerTokenProvider("serviceOAuth")).isSameAs(provider);

            NamedAuthenticationServiceClient firstClient = new NamedAuthenticationServiceClient(testingHttpClient, TEST_URI, credentials);
            io.airlift.openapi.client.generated.second.api.NamedAuthenticationServiceClient secondClient =
                    new io.airlift.openapi.client.generated.second.api.NamedAuthenticationServiceClient(testingHttpClient, TEST_URI, credentials);

            assertThat(firstClient.oauth().value()).isEqualTo("oauth");
            assertThat(secondClient.oauth().value()).isEqualTo("oauth");
            assertThat(tokenRequests).hasValue(2);
            assertThat(apiRequests).hasValue(3);
        }
    }

    @Test
    void testStructuredDeclaredAndRawFailures()
    {
        HeaderName requestId = HeaderName.of("X-Request-Id");
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> new io.airlift.http.client.testing.TestingResponse(
                CONFLICT,
                ImmutableListMultimap.of(
                        CONTENT_TYPE, JSON_UTF_8.toString(),
                        requestId, "request-secret"),
                "{\"code\":\"active-operation\"}".getBytes(UTF_8)))) {
            CompatibilityServiceClient client = new CompatibilityServiceClient(
                    testingHttpClient,
                    URI.create("http://user:password@example.test/api?token=query-secret#fragment"),
                    accessToken(ACCESS_TOKEN),
                    RetryPolicy.disabled());

            assertThatThrownBy(client::readFailure)
                    .isInstanceOf(ApiException.class)
                    .satisfies(throwable -> {
                        ApiException exception = (ApiException) throwable;
                        assertThat(exception.getOperationName()).isEqualTo("readFailure");
                        assertThat(exception.getRequestMethod()).isEqualTo("GET");
                        assertThat(exception.getRequestUri()).isEqualTo(URI.create("http://example.test/api/compatibility/api/v1/serviceStatus:read-failure"));
                        assertThat(exception.getStatusCode()).isEqualTo(409);
                        assertThat(exception.getHeaders().get(requestId)).containsExactly("request-secret");
                        assertThat(exception.getContentType()).contains(JSON_UTF_8.toString());
                        assertThat(exception.getResponseBody()).isEqualTo("{\"code\":\"active-operation\"}");
                        assertThat(exception.isResponseBodyTruncated()).isFalse();
                        assertThat(exception.getError(CompatibilityConflict.class))
                                .contains(new CompatibilityConflict("active-operation"));
                        assertThat(exception.getMessage())
                                .doesNotContain("request-secret", "active-operation", "password", "query-secret");
                        assertThat(exception.toString())
                                .doesNotContain("request-secret", "active-operation", "password", "query-secret");
                    });
        }

        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> mockResponse(CONFLICT, JSON_UTF_8, "{\"message\":\"active\"}"))) {
            CompatibilityServiceClient client = new CompatibilityServiceClient(testingHttpClient, TEST_URI, accessToken(ACCESS_TOKEN), RetryPolicy.disabled());
            assertThatThrownBy(() -> client.safeExecute("request-key"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(throwable -> {
                        ApiException exception = (ApiException) throwable;
                        assertThat(exception.getOperationName()).isEqualTo("safeExecute");
                        assertThat(exception.getRequestMethod()).isEqualTo("POST");
                        assertThat(exception.getStatusCode()).isEqualTo(409);
                        assertThat(exception.getResponseBody()).isEqualTo("{\"message\":\"active\"}");
                    });
        }

        assertRawFallback(mockResponse(CONFLICT, PLAIN_TEXT_UTF_8, "plain failure"), "plain failure", false);
        assertRawFallback(mockResponse(CONFLICT, JSON_UTF_8, "{malformed"), "{malformed", false);
        assertRawFallback(mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, "{\"code\":\"undeclared\"}"), "{\"code\":\"undeclared\"}", false);

        assertRawFallback(
                new TestingResponse(INTERNAL_SERVER_ERROR, ImmutableListMultimap.of(CONTENT_TYPE, "text/plain; charset=ISO-8859-1"), "caf\u00e9".getBytes(ISO_8859_1)),
                "caf\u00e9",
                false);

        String oversized = "{\"code\":\"" + "x".repeat(UnexpectedResponseException.DEFAULT_MAX_RESPONSE_BODY_BYTES) + "\"}";
        assertRawFallback(mockResponse(CONFLICT, JSON_UTF_8, oversized), oversized.substring(0, UnexpectedResponseException.DEFAULT_MAX_RESPONSE_BODY_BYTES), true);
    }

    private static HttpClientCredentials accessToken(String token)
    {
        return accessToken(BearerTokenProvider.fixedToken(token));
    }

    private static HttpClientCredentials accessToken(BearerTokenProvider tokenProvider)
    {
        return HttpClientCredentials.builder().bearerToken("accessToken", tokenProvider).build();
    }

    private static HttpClient streamingClient(StreamingResponse response)
    {
        return new HttpClient()
        {
            @Override
            public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamingResponse executeStreaming(Request request)
            {
                return response;
            }

            @Override
            public RequestStats getStats()
            {
                return new RequestStats();
            }

            @Override
            public void close() {}

            @Override
            public boolean isClosed()
            {
                return false;
            }
        };
    }

    private static final class StubStreamingResponse
            extends TestingResponse
            implements StreamingResponse
    {
        private final AtomicBoolean closed;

        private StubStreamingResponse(ListMultimap<HeaderName, String> headers, byte[] body, AtomicBoolean closed)
        {
            super(OK, headers, body);
            this.closed = closed;
        }

        @Override
        public void close()
        {
            closed.set(true);
        }
    }

    private static CompatibilityServiceClient client(TestingHttpClient httpClient, int maxRetries)
    {
        return new CompatibilityServiceClient(httpClient, TEST_URI, accessToken(ACCESS_TOKEN), new RetryPolicy(maxRetries, NO_DELAY, NO_DELAY));
    }

    private static void assertRawFallback(io.airlift.http.client.Response response, String expectedBody, boolean truncated)
    {
        try (TestingHttpClient testingHttpClient = new TestingHttpClient(_ -> response)) {
            CompatibilityServiceClient client = new CompatibilityServiceClient(testingHttpClient, TEST_URI, accessToken(ACCESS_TOKEN), RetryPolicy.disabled());
            assertThatThrownBy(client::readFailure)
                    .isInstanceOf(ApiException.class)
                    .satisfies(throwable -> {
                        ApiException exception = (ApiException) throwable;
                        assertThat(exception.getResponseBody()).isEqualTo(expectedBody);
                        assertThat(exception.isResponseBodyTruncated()).isEqualTo(truncated);
                        assertThat(exception.getError()).isEmpty();
                    });
        }
    }

    private static io.airlift.http.client.Response unauthorizedResponse(String... challenges)
    {
        ImmutableListMultimap.Builder<HeaderName, String> headers = ImmutableListMultimap.builder();
        headers.put(CONTENT_TYPE, JSON_UTF_8.toString());
        for (String challenge : challenges) {
            headers.put(WWW_AUTHENTICATE, challenge);
        }
        return new TestingResponse(UNAUTHORIZED, headers.build(), "{}".getBytes(UTF_8));
    }

    public static class BearerTokenFilter
            implements ContainerRequestFilter
    {
        @Override
        public void filter(ContainerRequestContext requestContext)
        {
            if (!("Bearer " + ACCESS_TOKEN).equals(requestContext.getHeaderString(AUTHORIZATION.toString()))) {
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                        .build());
            }
        }
    }
}
