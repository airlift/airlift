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
import io.airlift.api.binding.ApiModule;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.BearerTokenProvider;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.openapi.client.generated.api.CompatibilityServiceClient;
import io.airlift.openapi.client.generated.ApiException;
import io.airlift.openapi.client.generated.model.FrameKind;
import io.airlift.openapi.client.generated.model.QueryFrame;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedClientRuntimeTest
{
    private static final String ACCESS_TOKEN = "test-access-token";

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
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, ACCESS_TOKEN);

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
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, tokenProvider);
        io.airlift.openapi.client.generated.second.api.CompatibilityServiceClient secondClient =
                new io.airlift.openapi.client.generated.second.api.CompatibilityServiceClient(httpClient, baseUri, tokenProvider);

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
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, tokenProvider);

        assertThatThrownBy(client::getQueryFrame)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("after 1 attempts");
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
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, tokenProvider);

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
        CompatibilityServiceClient client = new CompatibilityServiceClient(httpClient, baseUri, tokenProvider);

        assertThatThrownBy(client::getQueryFrame)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("after 2 attempts");
        assertThat(tokenResolutions).hasValue(2);
        assertThat(refreshes).hasValue(1);
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
