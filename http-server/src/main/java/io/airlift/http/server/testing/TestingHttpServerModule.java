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
import com.google.inject.multibindings.Multibinder;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.http.server.HttpServer;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.LocalAnnouncementHttpServerInfo;
import io.airlift.http.server.TheServlet;

import javax.servlet.Filter;

public class TestingHttpServerModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        // Jetty scales required threads based on processor count, so pick a safe number
        int threads = Math.max(10, Runtime.getRuntime().availableProcessors());
        HttpServerConfig config = new HttpServerConfig().setMinThreads(1).setMaxThreads(threads).setHttpPort(0);

        binder.bind(HttpServerConfig.class).toInstance(config);
        binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        binder.bind(TestingHttpServer.class).in(Scopes.SINGLETON);
        binder.bind(HttpServer.class).to(Key.get(TestingHttpServer.class));
        Multibinder.newSetBinder(binder, Filter.class, TheServlet.class);
        binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class);
    }
}
