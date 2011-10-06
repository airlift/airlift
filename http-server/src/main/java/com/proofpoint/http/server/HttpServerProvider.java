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

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.proofpoint.node.NodeInfo;
import org.eclipse.jetty.security.LoginService;

import javax.annotation.Nullable;
import javax.management.MBeanServer;
import javax.servlet.Servlet;
import java.util.Map;

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
    private Map<String, String> servletInitParameters = ImmutableMap.of();
    private MBeanServer mbeanServer;
    private LoginService loginService;
    private final RequestStats stats;

    @Inject
    public HttpServerProvider(HttpServerInfo httpServerInfo, NodeInfo nodeInfo, HttpServerConfig config, @TheServlet Servlet theServlet, RequestStats stats)
    {
        Preconditions.checkNotNull(httpServerInfo, "httpServerInfo is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(theServlet, "theServlet is null");
        Preconditions.checkNotNull(stats, "stats is null");

        this.httpServerInfo = httpServerInfo;
        this.nodeInfo = nodeInfo;
        this.config = config;
        this.theServlet = theServlet;
        this.stats = stats;
    }

    @Inject(optional = true)
    public void setServletInitParameters(@TheServlet Map<String, String> parameters)
    {
        this.servletInitParameters = ImmutableMap.copyOf(parameters);
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

    public HttpServer get()
    {
        try {
            HttpServer httpServer = new HttpServer(httpServerInfo, nodeInfo, config, theServlet, servletInitParameters, mbeanServer, loginService, stats);
            httpServer.start();
            return httpServer;
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
