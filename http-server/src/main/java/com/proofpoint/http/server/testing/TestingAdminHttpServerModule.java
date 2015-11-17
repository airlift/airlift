/*
 * Copyright 2015 Proofpoint, Inc.
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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.http.server.HttpServer;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.http.server.LocalAnnouncementHttpServerInfo;
import com.proofpoint.http.server.QueryStringFilter;
import com.proofpoint.http.server.TheAdminServlet;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.http.server.testing.TestingAdminHttpServer.NullServlet;
import com.proofpoint.tracetoken.TraceTokenManager;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class TestingAdminHttpServerModule
        implements Module
{
    private final boolean initializeMainServlet;

    public TestingAdminHttpServerModule()
    {
        this(false);
    }

    private TestingAdminHttpServerModule(boolean initializeMainServlet)
    {
        this.initializeMainServlet = initializeMainServlet;
    }

    public static TestingAdminHttpServerModule initializesMainServletTestingAdminHttpServerModule() {
        return new TestingAdminHttpServerModule(true);
    }

    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        // Jetty scales required threads based on processor count, so pick a safe number
        int threads = Math.max(200, Runtime.getRuntime().availableProcessors() * 2);
        HttpServerConfig config = new HttpServerConfig().setMinThreads(1).setMaxThreads(threads).setHttpPort(0);

        binder.bind(TraceTokenManager.class).in(Scopes.SINGLETON);
        binder.bind(HttpServerConfig.class).toInstance(config);
        binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        binder.bind(TestingAdminHttpServer.class).in(Scopes.SINGLETON);
        binder.bind(HttpServer.class).to(Key.get(TestingAdminHttpServer.class));
        binder.bind(QueryStringFilter.class).in(Scopes.SINGLETON);
        newSetBinder(binder, Filter.class, TheAdminServlet.class);
        newSetBinder(binder, HttpResourceBinding.class, TheAdminServlet.class);
        binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class);

        if (initializeMainServlet) {
            binder.bind(Servlet.class).annotatedWith(ForTestingAdminHttpServer.class).to(Key.get(Servlet.class, TheServlet.class));
        }
        else {
            binder.bind(Servlet.class).annotatedWith(ForTestingAdminHttpServer.class).to(NullServlet.class);
        }
    }
}
