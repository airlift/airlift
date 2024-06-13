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
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.http3.server.HTTP3ServerConnectionFactory;
import org.eclipse.jetty.quic.server.QuicServerConnector;
import org.eclipse.jetty.quic.server.ServerQuicConfiguration;
import org.eclipse.jetty.server.AbstractNetworkConnector;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class TestingHttpServer
        implements AutoCloseable
{
    private final String scheme;
    private final Server server;
    private final HostAndPort hostAndPort;
    private final Path pemPath;

    public TestingHttpServer(Optional<String> keystore, Servlet servlet)
            throws Exception
    {
        this(keystore, servlet, ignored -> {}, Optional.empty());
    }

    public TestingHttpServer(Optional<String> keystore, Servlet servlet, Consumer<HttpConfiguration> configurationDecorator, Optional<Handler.Wrapper> additionalHandle)
            throws Exception
    {
        this.pemPath = Files.createTempDirectory("pems");
        requireNonNull(keystore, "keyStore is null");
        requireNonNull(servlet, "servlet is null");
        this.scheme = keystore.isPresent() ? "https" : "http";

        Server server = new Server();

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
            configureConnector(scheme, connector);
            connector.setReusePort(true);
            server.addConnector(connector);

            ServerQuicConfiguration quicConfig = new ServerQuicConfiguration(sslContextFactory, pemPath);
            QuicServerConnector quicServerConnector = new QuicServerConnector(server, quicConfig, new HTTP3ServerConnectionFactory(quicConfig));
            configureConnector("quic", quicServerConnector);
            server.addConnector(quicServerConnector);

            // Needed to support both dynamically assigned ports
            connector.addEventListener(new NetworkConnector.Listener()
            {
                @Override
                public void onOpen(NetworkConnector connector)
                {
                    int port = connector.getLocalPort();

                    // Configure the plain connector for secure redirects from http to https.
                    httpConfiguration.setSecurePort(port);
                    httpConfiguration.addCustomizer(new SvcResponseCustomizer(port));
                    // Configure the HTTP3 connector port to be the same as HTTPS/HTTP2.
                    quicServerConnector.setPort(port);
                }
            });
        }
        else {
            connector = new ServerConnector(server, insecureFactories(httpConfiguration));
            configureConnector(scheme, connector);
            connector.setReusePort(true);
            server.addConnector(connector);
        }

        ServletHolder servletHolder = new ServletHolder(servlet);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addServlet(servletHolder, "/*");

        ContextHandlerCollection handlers = new ContextHandlerCollection();
        handlers.addHandler(context);

        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setHandler(handlers);
        server.setHandler(gzipHandler);

        if (additionalHandle.isPresent()) {
            Handler.Wrapper handler = additionalHandle.get();
            handler.setHandler(gzipHandler);
            server.setHandler(handler);
        }
        else {
            server.setHandler(gzipHandler);
        }

        this.server = server;
        this.server.start();
        this.hostAndPort = HostAndPort.fromParts("localhost", connector.getLocalPort());
    }

    private static void configureConnector(String name, AbstractNetworkConnector connector)
    {
        connector.setIdleTimeout(30000);
        connector.setName(name);
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

    public static class SvcResponseCustomizer
            implements HttpConfiguration.Customizer
    {
        private final PreEncodedHttpField altSvcHttpField;

        public SvcResponseCustomizer(int quicPort)
        {
            altSvcHttpField = new PreEncodedHttpField(HttpHeader.ALT_SVC, format("h3=\":%d\"", quicPort));
        }

        @Override
        public org.eclipse.jetty.server.Request customize(Request request, HttpFields.Mutable responseHeaders)
        {
            responseHeaders.add(altSvcHttpField);
            return request;
        }
    }
}
