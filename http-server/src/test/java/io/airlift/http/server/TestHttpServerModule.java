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
import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
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
import static io.airlift.http.server.ServerFeature.LEGACY_URI_COMPLIANCE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
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

    @Test
    public void testCanConstructSingleServer()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        Bootstrap app = new Bootstrap(
                new HttpServerModule(),
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

    @Test
    public void testCanConstructMultipleServers()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "zhttp-request.log").getAbsolutePath())
                .put("internal.http-server.http.port", "0")
                .put("internal.http-server.log.path", new File(tempDir, "internal-http-request.log").getAbsolutePath())
                .build();

        Bootstrap app = new Bootstrap(
                new HttpServerModule("internal-server", Internal.class, "internal"),
                new HttpServerModule(),
                new TestingNodeModule(),
                new TracingModule("airlift.http-server", "1.0"),
                binder -> {
                    binder.bind(Key.get(Servlet.class, Internal.class)).to(DummyServlet.class);
                    binder.bind(Servlet.class).to(DummyServlet.class);
                });

        Injector injector = app
                .setRequiredConfigurationProperties(properties)
                .doNotInitializeLogging()
                .initialize();

        HttpServer primaryServer = injector.getInstance(HttpServer.class);
        HttpServerInfo primaryInfo = injector.getInstance(HttpServerInfo.class);

        HttpServer secondaryServer = injector.getInstance(Key.get(HttpServer.class, Internal.class));
        HttpServerInfo secondaryInfo = injector.getInstance(Key.get(HttpServerInfo.class, Internal.class));

        assertThat(primaryServer).isNotNull();
        assertThat(secondaryServer).isNotNull();

        assertThat(primaryServer).isNotEqualTo(secondaryServer);
        assertThat(primaryInfo.getHttpUri()).isNotEqualTo(secondaryInfo.getHttpUri());
    }

    @Test
    public void testHttpServerUri()
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        Bootstrap app = new Bootstrap(
                new HttpServerModule(),
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

    @Test
    public void testServer()
            throws Exception
    {
        doTestServerCompliance(true);
        doTestServerCompliance(false);
    }

    public void doTestServerCompliance(boolean enableLegacyUriCompliance)
            throws Exception
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.port", "0")
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .build();

        Bootstrap app = new Bootstrap(
                new HttpServerModule(),
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
                        httpServerBinder(binder).withFeature(LEGACY_URI_COMPLIANCE);
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

    @Retention(RUNTIME)
    @Target({FIELD, PARAMETER, METHOD})
    @BindingAnnotation
    public @interface Internal
    {
    }
}
