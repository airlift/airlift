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
package io.airlift.http.server.testing;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.http.server.HttpServer;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.LocalAnnouncementHttpServerInfo;
import io.airlift.http.server.TheServlet;

import javax.servlet.Filter;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.http.server.HttpServerBinder.HttpResourceBinding;

public class TestingHttpServerModule
        implements Module
{
    private final int httpPort;

    public TestingHttpServerModule()
    {
        this(0);
    }

    public TestingHttpServerModule(int httpPort)
    {
        this.httpPort = httpPort;
    }

    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        HttpServerConfig config = new HttpServerConfig().setHttpPort(httpPort);

        binder.bind(HttpServerConfig.class).toInstance(config);
        binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        binder.bind(TestingHttpServer.class).in(Scopes.SINGLETON);
        binder.bind(HttpServer.class).to(Key.get(TestingHttpServer.class));
        newSetBinder(binder, Filter.class, TheServlet.class);
        newSetBinder(binder, HttpResourceBinding.class, TheServlet.class);
        binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class);
    }
}
