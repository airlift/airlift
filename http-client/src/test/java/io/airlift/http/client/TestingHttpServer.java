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
import jakarta.servlet.Servlet;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.compression.gzip.GzipCompression;
import org.eclipse.jetty.compression.server.CompressionHandler;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class TestingHttpServer
        implements AutoCloseable
{
    private final String scheme;
    private final Server server;
    private final HostAndPort hostAndPort;

    public TestingHttpServer(Optional<String> keystore, Servlet servlet)
            throws Exception
    {
        this(keystore, servlet, httpConfiguration -> {}, Optional.empty());
    }

    public TestingHttpServer(Optional<String> keystore, Servlet servlet, Consumer<HttpConfiguration> configurationDecorator, Optional<Handler.Wrapper> additionalHandle)
            throws Exception
    {
        requireNonNull(keystore, "keyStore is null");
        requireNonNull(servlet, "servlet is null");
        this.scheme = keystore.isPresent() ? "https" : "http";

        Server server = new Server(null, null, null);

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setSendXPoweredBy(false);
        configurationDecorator.accept(httpConfiguration);

        ServerConnector connector;
        if (keystore.isPresent()) {
            httpConfiguration.addCustomizer(new SecureRequestCustomizer(false));

            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(keystore.get());
            sslContextFactory.setKeyStorePassword("changeit");
            connector = new ServerConnector(server, secureFactories(httpConfiguration, sslContextFactory));
        }
        else {
            connector = new ServerConnector(server, insecureFactories(httpConfiguration));
        }

        connector.setIdleTimeout(30000);
        connector.setName(keystore.map(path -> "https").orElse("http"));

        server.addConnector(connector);

        ServletHolder servletHolder = new ServletHolder(servlet);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addServlet(servletHolder, "/*");

        ContextHandlerCollection handlers = new ContextHandlerCollection();
        handlers.addHandler(context);

        CompressionHandler compressionHandler = new CompressionHandler();
        compressionHandler.putCompression(new GzipCompression());
        compressionHandler.setHandler(handlers);
        server.setHandler(compressionHandler);

        if (additionalHandle.isPresent()) {
            Handler.Wrapper handler = additionalHandle.get();
            handler.setHandler(compressionHandler);
            server.setHandler(handler);
        }
        else {
            server.setHandler(compressionHandler);
        }

        this.server = server;
        this.server.start();
        this.hostAndPort = HostAndPort.fromParts("localhost", connector.getLocalPort());
    }

    private ConnectionFactory[] insecureFactories(HttpConfiguration httpConfiguration)
    {
        HttpConnectionFactory http1 = new HttpConnectionFactory(httpConfiguration);
        HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(httpConfiguration);
        return new ConnectionFactory[] {http1, http2c};
    }

    private ConnectionFactory[] secureFactories(HttpConfiguration httpsConfiguration, SslContextFactory.Server server)
    {
        ConnectionFactory http1 = new HttpConnectionFactory(httpsConfiguration);
        ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
        alpn.setDefaultProtocol(http1.getProtocol());

        SslConnectionFactory tls = new SslConnectionFactory(server, alpn.getProtocol());
        HTTP2ServerConnectionFactory http2 = new HTTP2ServerConnectionFactory(httpsConfiguration);

        return new ConnectionFactory[] {tls, alpn, http2, http1};
    }

    public HostAndPort getHostAndPort()
    {
        return hostAndPort;
    }

    public URI baseURI()
    {
        try {
            return new URI(scheme, null, hostAndPort.getHost(), hostAndPort.getPort(), null, null, null);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()
            throws Exception
    {
        server.stop();
    }
}
