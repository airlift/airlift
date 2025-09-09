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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import io.airlift.node.NodeInfo;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.management.MBeanServer;

import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.util.Objects.requireNonNull;

/**
 * Provides an instance of a Jetty server ready to be configured with
 * com.google.inject.servlet.ServletModule
 */
public class HttpServerProvider
        implements Provider<HttpServer>
{
    private final HttpServerInfo httpServerInfo;
    private final NodeInfo nodeInfo;
    private final HttpServerConfig config;
    private final Optional<HttpsConfig> httpsConfig;
    private final Servlet servlet;
    private final Set<HttpResourceBinding> resources;
    private final ClientCertificate clientCertificate;
    private final boolean enableVirtualThreads;
    private final boolean enableLegacyUriCompliance;
    private final boolean enableCaseSensitiveHeaderCache;
    private Optional<MBeanServer> mbeanServer = Optional.empty();
    private final Set<Filter> filters;
    private final Optional<SslContextFactory.Server> sslContextFactory;
    private final Optional<ByteBufferPool> byteBufferPool;

    @Inject
    public HttpServerProvider(HttpServerInfo httpServerInfo,
                  NodeInfo nodeInfo,
                  HttpServerConfig config,
                  Optional<HttpsConfig> httpsConfig,
                  Servlet servlet,
                  Set<Filter> filters,
                  Set<HttpResourceBinding> resources,
                  @EnableVirtualThreads boolean enableVirtualThreads,
                  @EnableLegacyUriCompliance boolean enableLegacyUriCompliance,
                  @EnableCaseSensitiveHeaderCache boolean enableCaseSensitiveHeaderCache,
                  ClientCertificate clientCertificate,
                  Optional<SslContextFactory.Server> sslContextFactory,
                  Optional<ByteBufferPool> byteBufferPool)
    {
        requireNonNull(httpServerInfo, "httpServerInfo is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(config, "config is null");
        requireNonNull(httpsConfig, "httpsConfig is null");
        requireNonNull(servlet, "servlet is null");
        requireNonNull(filters, "filters is null");
        requireNonNull(resources, "resources is null");
        requireNonNull(clientCertificate, "clientCertificate is null");
        requireNonNull(sslContextFactory, "sslContextFactory is null");
        requireNonNull(byteBufferPool, "byteBufferPool is null");

        this.httpServerInfo = httpServerInfo;
        this.nodeInfo = nodeInfo;
        this.config = config;
        this.httpsConfig = httpsConfig;
        this.servlet = servlet;
        this.filters = ImmutableSet.copyOf(filters);
        this.resources = ImmutableSet.copyOf(resources);
        this.enableVirtualThreads = enableVirtualThreads;
        this.enableLegacyUriCompliance = enableLegacyUriCompliance;
        this.enableCaseSensitiveHeaderCache = enableCaseSensitiveHeaderCache;
        this.clientCertificate = clientCertificate;
        this.sslContextFactory = sslContextFactory;
        this.byteBufferPool = byteBufferPool;
    }

    @Inject(optional = true)
    public void setMBeanServer(MBeanServer server)
    {
        mbeanServer = Optional.of(server);
    }

    @Override
    public HttpServer get()
    {
        try {
            HttpServer httpServer = new HttpServer(
                    httpServerInfo,
                    nodeInfo,
                    config,
                    httpsConfig,
                    servlet,
                    filters,
                    resources,
                    enableVirtualThreads,
                    enableLegacyUriCompliance,
                    enableCaseSensitiveHeaderCache,
                    clientCertificate,
                    mbeanServer,
                    sslContextFactory,
                    byteBufferPool);
            httpServer.start();
            return httpServer;
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }
}
