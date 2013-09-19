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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceTokenManager;
import org.eclipse.jetty.security.LoginService;

import javax.annotation.Nullable;
import javax.management.MBeanServer;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

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
    private final Servlet theServlet;
    private final Set<HttpResourceBinding> resources;
    private Map<String, String> servletInitParameters = ImmutableMap.of();
    private Map<String, String> adminServletInitParameters = ImmutableMap.of();
    private MBeanServer mbeanServer;
    private LoginService loginService;
    private final RequestStats stats;
    private final DetailedRequestStats detailedRequestStats;
    private final Set<Filter> filters;
    private final Set<Filter> adminFilters;
    private QueryStringFilter queryStringFilter;
    private TraceTokenManager traceTokenManager;

    @Inject
    public HttpServerProvider(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            @TheServlet Servlet theServlet,
            @TheServlet Set<Filter> filters,
            @TheServlet Set<HttpResourceBinding> resources,
            @TheAdminServlet Set<Filter> adminFilters,
            RequestStats stats,
            DetailedRequestStats detailedRequestStats,
            QueryStringFilter queryStringFilter)
    {
        checkNotNull(httpServerInfo, "httpServerInfo is null");
        checkNotNull(nodeInfo, "nodeInfo is null");
        checkNotNull(config, "config is null");
        checkNotNull(theServlet, "theServlet is null");
        checkNotNull(filters, "filters is null");
        checkNotNull(resources, "resources is null");
        checkNotNull(adminFilters, "adminFilters is null");
        checkNotNull(stats, "stats is null");
        checkNotNull(detailedRequestStats, "detailedRequestStats is null");
        checkNotNull(queryStringFilter, "queryStringFilter is null");

        this.httpServerInfo = httpServerInfo;
        this.nodeInfo = nodeInfo;
        this.config = config;
        this.theServlet = theServlet;
        this.filters = ImmutableSet.copyOf(filters);
        this.resources = ImmutableSet.copyOf(resources);
        this.adminFilters = ImmutableSet.copyOf(adminFilters);
        this.stats = stats;
        this.detailedRequestStats = detailedRequestStats;
        this.queryStringFilter = queryStringFilter;
    }

    @Inject(optional = true)
    public void setServletInitParameters(@TheServlet Map<String, String> parameters)
    {
        this.servletInitParameters = ImmutableMap.copyOf(parameters);
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

    public HttpServer get()
    {
        try {
            HttpServer httpServer = new HttpServer(httpServerInfo,
                    nodeInfo,
                    config,
                    theServlet,
                    servletInitParameters,
                    filters,
                    resources,
                    adminServletInitParameters,
                    adminFilters,
                    mbeanServer,
                    loginService,
                    queryStringFilter,
                    traceTokenManager,
                    stats,
                    detailedRequestStats
            );
            httpServer.start();
            return httpServer;
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
