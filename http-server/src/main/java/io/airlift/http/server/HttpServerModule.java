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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import org.weakref.jmx.guice.MBeanModule;

import javax.servlet.Filter;

import static io.airlift.event.client.EventBinder.eventBinder;

/**
 * Provides a fully configured instance of an http server,
 * ready to use with Guice (via {@link com.google.inject.servlet.GuiceFilter})
 * <p/>
 * Features:
 *    - HTTP/HTTPS
 *    - Basic Auth
 *    - Request logging
 *    - JMX
 *
 * Configuration options are provided via {@link HttpServerConfig}
 * <p/>
 * To enable JMX, an {@link javax.management.MBeanServer} must be bound elsewhere
 * To enable Basic Auth, a {@link org.eclipse.jetty.security.LoginService} must be bound elsewhere
 * <p/>
 * To enable HTTPS, HttpServerConfig.isHttpsEnabled() must return true and HttpServerConfig.getKeystorePath() and
 * HttpServerConfig.getKeystorePassword() must return the path to the keystore containing the ssl cert & the password to the
 * keystore, respectively. The https port is specified via HttpServerConfig.getHttpsPort()
 */
public class HttpServerModule
        implements Module
{
    public static final String REALM_NAME = "Airlift";

    @Override
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        binder.bind(HttpServer.class).toProvider(HttpServerProvider.class).in(Scopes.SINGLETON);
        binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        binder.bind(RequestStats.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder, Filter.class, TheServlet.class);
        Multibinder.newSetBinder(binder, Filter.class, TheAdminServlet.class);
        Multibinder.newSetBinder(binder, HttpResourceBinding.class, TheServlet.class);

        MBeanModule.newExporter(binder).export(RequestStats.class).withGeneratedName();

        ConfigurationModule.bindConfig(binder).to(HttpServerConfig.class);

        eventBinder(binder).bindEventClient(HttpRequestEvent.class);

        binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class).in(Scopes.SINGLETON);
    }
}
