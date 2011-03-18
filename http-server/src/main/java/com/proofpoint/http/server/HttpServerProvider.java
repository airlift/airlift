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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.proofpoint.node.NodeInfo;
import org.eclipse.jetty.security.LoginService;

import javax.annotation.Nullable;
import javax.management.MBeanServer;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Map;

/**
 * Provides an instance of a Jetty server ready to be configured with
 * com.google.inject.servlet.ServletModule
 */
public class HttpServerProvider
        implements Provider<HttpServer>
{
    private final NodeInfo nodeInfo;
    private final HttpServerConfig config;
    private final Servlet theServlet;
    private Map<String, String> servletInitParameters = ImmutableMap.of();
    private MBeanServer mbeanServer;
    private LoginService loginService;

    @Inject
    public HttpServerProvider(NodeInfo nodeInfo, HttpServerConfig config, @TheServlet Servlet theServlet)
    {
        this.nodeInfo = nodeInfo;
        this.config = config;
        this.theServlet = theServlet;
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
            return new HttpServer(nodeInfo, config, theServlet, servletInitParameters, mbeanServer, loginService);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
