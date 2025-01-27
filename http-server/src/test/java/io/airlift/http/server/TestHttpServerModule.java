/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
package io.airlift.http.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.HttpUriBuilder;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.log.Logging;
import io.airlift.node.NodeInfo;
import io.airlift.node.testing.TestingNodeModule;
import io.airlift.tracing.TracingModule;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.LOCATION;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.http.server.HttpServerBinder.httpServerBinder;
import static java.nio.file.Files.createTempDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestHttpServerModule
{
    private File tempDir;

    @BeforeAll
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeEach
    public void setup()
            throws IOException
    {
        tempDir = createTempDirectory(null).toFile();
    }

    @AfterEach
    public void tearDown()
            throws IOException
    {
        deleteRecursively(tempDir.toPath(), ALLOW_INSECURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "testPrefix"})
    public void testCanConstructServer(String configPrefix)
    {
        String propertyPrefix = configPrefix.isEmpty() ? "" : (configPrefix + ".");
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put(propertyPrefix + "http-server.http.port", "0")
                .put(propertyPrefix + "http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        HttpServerModule httpServerModule = configPrefix.isEmpty() ? new HttpServerModule() : new HttpServerModule(configPrefix);
        Bootstrap app = new Bootstrap(
                httpServerModule,
                new TestingNodeModule(),
                new TracingModule("airlift.http-server", "1.0"),
                binder -> binder.bind(Servlet.class).to(DummyServlet.class));

        Injector injector = app
                .setRequiredConfigurationProperties(properties)
                .doNotInitializeLogging()
                .initialize();

        HttpServer server = injector.getInstance(HttpServer.class);
        assertThat(server).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "testPrefix"})
    public void testHttpServerUri(String configPrefix)
            throws Exception
    {
        String propertyPrefix = configPrefix.isEmpty() ? "" : (configPrefix + ".");
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put(propertyPrefix + "http-server.http.port", "0")
                .put(propertyPrefix + "http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        HttpServerModule httpServerModule = configPrefix.isEmpty() ? new HttpServerModule() : new HttpServerModule(configPrefix);
        Bootstrap app = new Bootstrap(
                httpServerModule,
                new TestingNodeModule(),
                new TracingModule("airlift.http-server", "1.0"),
                binder -> binder.bind(Servlet.class).to(DummyServlet.class));

        Injector injector = app
                .setRequiredConfigurationProperties(properties)
                .doNotInitializeLogging()
                .initialize();

        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        HttpServer server = injector.getInstance(HttpServer.class);
        assertThat(server).isNotNull();
        server.start();
        try {
            HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
            assertThat(httpServerInfo).isNotNull();
            assertThat(httpServerInfo.getHttpUri()).isNotNull();
            assertThat(httpServerInfo.getHttpUri().getScheme()).isEqualTo("http");
            assertThat(httpServerInfo.getHttpUri().getHost()).isEqualTo(nodeInfo.getInternalAddress());
            assertThat(httpServerInfo.getHttpsUri()).isNull();
        }
        catch (Exception e) {
            server.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "testPrefix"})
    public void testServer(String configPrefix)
            throws Exception
    {
        doTestServerCompliance(configPrefix, true);
        doTestServerCompliance(configPrefix, false);
    }

    public void doTestServerCompliance(String configPrefix, boolean enableLegacyUriCompliance)
            throws Exception
    {
        String propertyPrefix = configPrefix.isEmpty() ? "" : (configPrefix + ".");
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put(propertyPrefix + "http-server.http.port", "0")
                .put(propertyPrefix + "http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        HttpServerModule httpServerModule = configPrefix.isEmpty() ? new HttpServerModule() : new HttpServerModule(configPrefix);
        Bootstrap app = new Bootstrap(
                httpServerModule,
                new TestingNodeModule(),
                new TracingModule("airlift.http-server", "1.0"),
                binder -> {
                    binder.bind(Servlet.class).to(DummyServlet.class);
                    newSetBinder(binder, Filter.class).addBinding().to(DummyFilter.class).in(Scopes.SINGLETON);
                    httpServerBinder(binder).bindResource("/", "webapp/user").withWelcomeFile("user-welcome.txt");
                    httpServerBinder(binder).bindResource("/", "webapp/user2");
                    httpServerBinder(binder).bindResource("path", "webapp/user").withWelcomeFile("user-welcome.txt");
                    httpServerBinder(binder).bindResource("path", "webapp/user2");
                    if (enableLegacyUriCompliance) {
                        httpServerBinder(binder).enableLegacyUriCompliance();
                    }
                });

        Injector injector = app
                .setRequiredConfigurationProperties(properties)
                .doNotInitializeLogging()
                .initialize();

        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);

        HttpServer server = injector.getInstance(HttpServer.class);
        server.start();

        try (HttpClient client = new JettyHttpClient()) {
            // test servlet bound correctly
            URI httpUri = httpServerInfo.getHttpUri();
            StatusResponse response = client.execute(prepareGet().setUri(httpUri).build(), createStatusResponseHandler());

            assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);

            // test filter bound correctly
            response = client.execute(prepareGet().setUri(httpUri.resolve("/filter")).build(), createStatusResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_PAYMENT_REQUIRED);

            if (enableLegacyUriCompliance) {
                // test legacy URI code for encoded slashes
                response = client.execute(prepareGet().setUri(httpUri.resolve("/slashtest/one/two%2fthree/four/%2f/five")).build(), createStatusResponseHandler());
                assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
            }

            // test http resources
            assertResource(httpUri, client, "", "welcome user!");
            assertResource(httpUri, client, "user-welcome.txt", "welcome user!");
            assertResource(httpUri, client, "user.txt", "user");
            assertResource(httpUri, client, "user2.txt", "user2");
            assertRedirect(httpUri, client, "path", "/path/");
            assertResource(httpUri, client, "path/", "welcome user!");
            assertResource(httpUri, client, "path/user-welcome.txt", "welcome user!");
            assertResource(httpUri, client, "path/user.txt", "user");
            assertResource(httpUri, client, "path/user2.txt", "user2");
        }
        finally {
            server.stop();
        }
    }

    private void assertResource(URI baseUri, HttpClient client, String path, String contents)
    {
        HttpUriBuilder uriBuilder = uriBuilderFrom(baseUri);
        StringResponse response = client.execute(prepareGet().setUri(uriBuilder.appendPath(path).build()).build(), createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.code());
        String contentType = response.getHeader(CONTENT_TYPE);
        assertThat(contentType).as(CONTENT_TYPE + " header is absent").isNotNull();
        MediaType mediaType = MediaType.parse(contentType);
        assertThat(PLAIN_TEXT_UTF_8.is(mediaType)).as("Expected text/plain but got " + mediaType).isTrue();
        assertThat(response.getBody().trim()).isEqualTo(contents);
    }

    private void assertRedirect(URI baseUri, HttpClient client, String path, String redirect)
    {
        HttpUriBuilder uriBuilder = uriBuilderFrom(baseUri);
        StringResponse response = client.execute(
                prepareGet()
                        .setFollowRedirects(false)
                        .setUri(uriBuilder.appendPath(path).build())
                        .build(),
                createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT.code());
        assertThat(response.getHeader(LOCATION)).isEqualTo(redirect);
        assertThat(response.getHeader(CONTENT_TYPE)).as(CONTENT_TYPE + " header should be absent").isNull();
        assertThat(response.getBody()).as("Response body").isEqualTo("");
    }
}
