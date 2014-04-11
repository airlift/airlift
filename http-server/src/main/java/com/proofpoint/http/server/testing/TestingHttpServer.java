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
package com.proofpoint.http.server.testing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.proofpoint.http.server.HttpServer;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.http.server.QueryStringFilter;
import com.proofpoint.http.server.RequestStats;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.stats.TimeStat;
import com.proofpoint.tracetoken.TraceTokenManager;
import org.eclipse.jetty.server.handler.RequestLogHandler;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * HTTP server that binds to localhost on a random port
 */
public class TestingHttpServer extends HttpServer
{

    private final HttpServerInfo httpServerInfo;

    public TestingHttpServer(
            HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Servlet servlet,
            Map<String, String> initParameters)
            throws IOException
    {
        this(httpServerInfo,
                nodeInfo,
                config,
                servlet,
                initParameters,
                ImmutableSet.<Filter>of(),
                ImmutableSet.<HttpResourceBinding>of(),
                new QueryStringFilter(),
                new TraceTokenManager());
    }

    @Inject
    public TestingHttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            @TheServlet Servlet servlet,
            @TheServlet Map<String, String> initParameters,
            @TheServlet Set<Filter> filters,
            @TheServlet Set<HttpResourceBinding> resources,
            QueryStringFilter queryStringFilter,
            TraceTokenManager traceTokenManager)
            throws IOException
    {
        super(httpServerInfo,
                nodeInfo,
                config,
                servlet,
                initParameters,
                ImmutableSet.copyOf(filters),
                resources,
                ImmutableMap.<String, String>of(),
                ImmutableSet.<Filter>of(),
                null,
                null,
                queryStringFilter,
                traceTokenManager,
                new RequestStats(),
                new DetailedRequestStats()
        );
        this.httpServerInfo = httpServerInfo;
    }

    @Override
    public RequestLogHandler createLogHandler(HttpServerConfig config, TraceTokenManager tokenManager)
            throws IOException
    {
        return null;
    }

    public URI getBaseUrl()
    {
        return httpServerInfo.getHttpUri();
    }

    public int getPort()
    {
        return httpServerInfo.getHttpUri().getPort();
    }

    public static class DetailedRequestStats implements com.proofpoint.http.server.DetailedRequestStats
    {
        @Override
        public TimeStat requestTimeByCode(int responseCode)
        {
            return new TimeStat();
        }
    }
}
