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
package com.proofpoint.http.server;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.ning.http.util.Base64;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.node.NodeModule;
import org.eclipse.jetty.security.LoginService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestHttpServerProvider
{
    private HttpServer server;
    private File tempDir;
    private HttpServerInfo httpServerInfo;
    private int httpPort = -1;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        try {
            if (server != null) {
                server.stop();
            }
        }
        finally {
            Files.deleteRecursively(tempDir);
        }
    }

    @Test
    public void testHttp()
            throws Exception
    {
        createServer(true);
        server.start();

        AsyncHttpClient client = new AsyncHttpClient();
        Response response = client.prepareGet(httpServerInfo.getHttpUri().toString())
                .execute()
                .get();

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testHttpIsDisabled()
            throws Exception
    {
        createServer(false);
        server.start();

        AsyncHttpClient client = new AsyncHttpClient();
        try {
            Response response = client.prepareGet("http://localhost:" + httpPort + "/")
                    .execute()
                    .get();

            if (response != null) { // TODO: this is a workaround for a bug in AHC (some race condition)
                fail("Expected connection refused, got response code: " + response.getStatusCode());
            }
        }
        catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ConnectException);
        }
    }

    @Test
    public void testAuth()
            throws Exception
    {
        createServer(true);
        server.start();

        AsyncHttpClient client = new AsyncHttpClient();
        Response response = client.prepareGet(httpServerInfo.getHttpUri().toString())
                .addHeader("Authorization", "Basic " + Base64.encode("user:password".getBytes()))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
        assertEquals(response.getResponseBody(), "user");
    }

    private void createServer(boolean httpEnabled)
            throws Exception
    {
        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(0));
            httpPort = socket.getLocalPort();
        }
        finally {
            socket.close();
        }

        File file = File.createTempFile("auth", ".properties", tempDir);
        Files.write("user: password", file, Charsets.UTF_8);

        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "test")
                .put("http-server.http.port", String.valueOf(httpPort))
                .put("http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath())
                .put("http-server.http.enabled", String.valueOf(httpEnabled))
                .put("http-server.auth.users-file", file.getAbsolutePath())
                .build();

        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new HttpServerModule(),
                new NodeModule(),
                new ConfigurationModule(configFactory),
                new ServletModule()
                {
                    @Override
                    public void configureServlets()
                    {
                        bind(DummyServlet.class).in(Scopes.SINGLETON);
                        bind(LoginService.class).toProvider(HashLoginServiceProvider.class);
                        serve("/*").with(DummyServlet.class);
                    }
                });

        server = injector.getInstance(HttpServer.class);
        httpServerInfo = injector.getInstance(HttpServerInfo.class);
        server.start();
    }
}
