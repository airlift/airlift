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
import com.google.inject.Key;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import io.airlift.http.server.tracing.TracingServletFilter;
import jakarta.servlet.Filter;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConditionalModule.conditionalModule;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

/**
 * Provides a fully configured instance of an HTTP server,
 * ready to use with Guice.
 * <p>
 * Features:
 * <ul>
 * <li>HTTP/HTTPS</li>
 * <li>Basic Auth</li>
 * <li>Request logging</li>
 * <li>JMX</li>
 * </ul>
 * Configuration options are provided via {@link HttpServerConfig}
 * <p>
 * To enable JMX, an {@link javax.management.MBeanServer} must be bound elsewhere
 * <p>
 * To enable HTTPS, {@link HttpServerConfig#isHttpsEnabled()} must return true
 * and {@link HttpsConfig#getKeystorePath()}
 * and {@link HttpsConfig#getKeystorePassword()} must return the path to
 * the keystore containing the SSL cert and the password to the keystore, respectively.
 * The HTTPS port is specified via {@link HttpsConfig#getHttpsPort()}.
 */
public class HttpServerModule
        extends AbstractConfigurationAwareModule
{
    @Override
    protected void setup(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(HttpServer.class).toProvider(HttpServerProvider.class).in(Scopes.SINGLETON);
        newOptionalBinder(binder, ClientCertificate.class).setDefault().toInstance(ClientCertificate.NONE);
        newExporter(binder).export(HttpServer.class).withGeneratedName();
        binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        // override with HttpServerBinder.enableVirtualThreads()
        newOptionalBinder(binder, Key.get(Boolean.class, EnableVirtualThreads.class)).setDefault().toInstance(false);
        // override with HttpServerBinder.enableLegacyUriCompliance()
        newOptionalBinder(binder, Key.get(Boolean.class, EnableLegacyUriCompliance.class)).setDefault().toInstance(false);
        newSetBinder(binder, Filter.class).addBinding()
                .to(TracingServletFilter.class).in(Scopes.SINGLETON);
        newSetBinder(binder, HttpResourceBinding.class);
        newOptionalBinder(binder, SslContextFactory.Server.class);
        newOptionalBinder(binder, Key.get(Boolean.class, EnableCaseSensitiveHeaderCache.class)).setDefault().toInstance(false);

        configBinder(binder).bindConfig(HttpServerConfig.class);
        newOptionalBinder(binder, HttpsConfig.class);

        binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class).in(Scopes.SINGLETON);

        install(conditionalModule(HttpServerConfig.class, HttpServerConfig::isHttpsEnabled, moduleBinder ->
                configBinder(moduleBinder).bindConfig(HttpsConfig.class)));
    }
}
