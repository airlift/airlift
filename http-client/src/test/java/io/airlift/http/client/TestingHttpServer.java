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

import com.google.common.net.HostAndPort;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.Servlet;

import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class TestingHttpServer
        implements AutoCloseable
{
    private final Server server;
    private final HostAndPort hostAndPort;

    public TestingHttpServer(Optional<String> keystore, Servlet servlet)
            throws Exception
    {
        this(keystore, servlet, httpConfiguration -> {}, Optional.empty());
    }

    public TestingHttpServer(Optional<String> keystore, Servlet servlet, Consumer<HttpConfiguration> configurationDecorator, Optional<Handler> additionalHandle)
            throws Exception
    {
        requireNonNull(keystore, "keyStore is null");
        requireNonNull(servlet, "servlet is null");

        Server server = new Server();

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setSendXPoweredBy(false);
        configurationDecorator.accept(httpConfiguration);

        ServerConnector connector;
        if (keystore.isPresent()) {
            httpConfiguration.addCustomizer(new SecureRequestCustomizer());

            SslContextFactory sslContextFactory = new SslContextFactory.Client();
            sslContextFactory.setKeyStorePath(keystore.get());
            sslContextFactory.setKeyStorePassword("changeit");
            SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, "http/1.1");

            connector = new ServerConnector(server, sslConnectionFactory, new HttpConnectionFactory(httpConfiguration));
        }
        else {
            HttpConnectionFactory http1 = new HttpConnectionFactory(httpConfiguration);
            HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(httpConfiguration);

            connector = new ServerConnector(server, http1, http2c);
        }

        connector.setIdleTimeout(30000);
        connector.setName(keystore.map(path -> "https").orElse("http"));

        server.addConnector(connector);

        ServletHolder servletHolder = new ServletHolder(servlet);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setGzipHandler(new GzipHandler());
        context.addServlet(servletHolder, "/*");

        HandlerCollection handlers = new HandlerCollection();
        additionalHandle.ifPresent(handlers::addHandler);
        handlers.addHandler(context);

        server.setHandler(handlers);

        this.server = server;
        this.server.start();
        this.hostAndPort = HostAndPort.fromParts("localhost", connector.getLocalPort());
    }

    public HostAndPort getHostAndPort()
    {
        return hostAndPort;
    }

    @Override
    public void close()
            throws Exception
    {
        server.setStopTimeout(3000);
        server.stop();
    }
}
