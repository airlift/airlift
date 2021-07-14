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
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.event.client.EventClient;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import io.airlift.node.NodeInfo;
import io.airlift.tracetoken.TraceTokenManager;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.management.MBeanServer;
import javax.servlet.Filter;
import javax.servlet.Servlet;

import java.util.Map;
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
    private final Servlet theServlet;
    private final Set<HttpResourceBinding> resources;
    private final ClientCertificate clientCertificate;
    private Map<String, String> servletInitParameters = ImmutableMap.of();
    private Servlet theAdminServlet;
    private Map<String, String> adminServletInitParameters = ImmutableMap.of();
    private MBeanServer mbeanServer;
    private LoginService loginService;
    private final RequestStats stats;
    private final Set<Filter> filters;
    private final Set<Filter> adminFilters;
    private TraceTokenManager traceTokenManager;
    private final EventClient eventClient;
    private final Optional<SslContextFactory.Server> sslContextFactory;

    @Inject
    public HttpServerProvider(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Optional<HttpsConfig> httpsConfig,
            @TheServlet Servlet theServlet,
            @TheServlet Set<Filter> filters,
            @TheServlet Set<HttpResourceBinding> resources,
            @TheAdminServlet Set<Filter> adminFilters,
            ClientCertificate clientCertificate,
            RequestStats stats,
            EventClient eventClient,
            Optional<SslContextFactory.Server> sslContextFactory)
    {
        requireNonNull(httpServerInfo, "httpServerInfo is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(config, "config is null");
        requireNonNull(httpsConfig, "httpsConfig is null");
        requireNonNull(theServlet, "theServlet is null");
        requireNonNull(filters, "filters is null");
        requireNonNull(resources, "resources is null");
        requireNonNull(adminFilters, "adminFilters is null");
        requireNonNull(clientCertificate, "clientCertificate is null");
        requireNonNull(stats, "stats is null");
        requireNonNull(eventClient, "eventClient is null");
        requireNonNull(sslContextFactory, "sslContextFactory is null");

        this.httpServerInfo = httpServerInfo;
        this.nodeInfo = nodeInfo;
        this.config = config;
        this.httpsConfig = httpsConfig;
        this.theServlet = theServlet;
        this.filters = ImmutableSet.copyOf(filters);
        this.resources = ImmutableSet.copyOf(resources);
        this.adminFilters = ImmutableSet.copyOf(adminFilters);
        this.clientCertificate = clientCertificate;
        this.stats = stats;
        this.eventClient = eventClient;
        this.sslContextFactory = sslContextFactory;
    }

    @Inject(optional = true)
    public void setServletInitParameters(@TheServlet Map<String, String> parameters)
    {
        this.servletInitParameters = ImmutableMap.copyOf(parameters);
    }

    @Inject(optional = true)
    public void setTheAdminServlet(@TheAdminServlet Servlet theAdminServlet)
    {
        this.theAdminServlet = theAdminServlet;
    }

    @Inject(optional = true)
    public void setAdminServletInitParameters(@TheAdminServlet Map<String, String> parameters)
    {
        this.adminServletInitParameters = ImmutableMap.copyOf(parameters);
    }

    @Inject(optional = true)
    public void setMBeanServer(MBeanServer server)
    {
        mbeanServer = server;
    }

    @Inject(optional = true)
    public void setLoginService(@Nullable LoginService loginService)
    {
        this.loginService = loginService;
    }

    @Inject(optional = true)
    public void setTokenManager(@Nullable TraceTokenManager tokenManager)
    {
        this.traceTokenManager = tokenManager;
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
                    theServlet,
                    servletInitParameters,
                    filters,
                    resources,
                    theAdminServlet,
                    adminServletInitParameters,
                    adminFilters,
                    clientCertificate,
                    mbeanServer,
                    loginService,
                    traceTokenManager,
                    stats,
                    eventClient,
                    sslContextFactory);
            httpServer.start();
            return httpServer;
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }
}
