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
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import io.airlift.http.server.tracing.TracingServletFilter;
import jakarta.annotation.Nullable;
import jakarta.servlet.Filter;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;
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
    private final String name;
    private final Optional<Class<? extends Annotation>> qualifier;
    private final @Nullable String configPrefix;

    public HttpServerModule(String name, Optional<Class<? extends Annotation>> qualifier, @Nullable String configPrefix)
    {
        this.name = requireNonNull(name, "name is null");
        this.qualifier = requireNonNull(qualifier, "qualifier is null");
        this.configPrefix = configPrefix;

        if (this.qualifier.isPresent()) {
            checkArgument(this.configPrefix != null, "qualifier is present but configPrefix is null");
        }
    }

    public HttpServerModule(String name, Class<? extends Annotation> qualifier, @Nullable String configPrefix)
    {
        this(name, Optional.of(requireNonNull(qualifier, "qualifier is null")), configPrefix);
    }

    public HttpServerModule()
    {
        this("http-server", Optional.empty(), null);
    }

    @Override
    protected void setup(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(qualifiedKey(HttpServer.class))
                .toProvider(new HttpServerProvider(name, qualifier))
                .in(SINGLETON);
        newOptionalBinder(binder, qualifiedKey(ClientCertificate.class)).setDefault().toInstance(ClientCertificate.NONE);
        newExporter(binder).export(qualifiedKey(HttpServer.class)).withGeneratedName();
        newSetBinder(binder, qualifiedKey(ServerFeature.class));
        newSetBinder(binder, qualifiedKey(Filter.class)).addBinding()
                .to(TracingServletFilter.class)
                .in(SINGLETON);
        newSetBinder(binder, qualifiedKey(HttpResourceBinding.class));
        newOptionalBinder(binder, qualifiedKey(SslContextFactory.Server.class));
        configBinder(binder).bindConfig(qualifiedKey(HttpServerConfig.class), HttpServerConfig.class, configPrefix);
        newOptionalBinder(binder, qualifiedKey(HttpsConfig.class));

        binder.bind(qualifiedKey(AnnouncementHttpServerInfo.class))
                .toProvider(new AnnouncementHttpServerInfoProvider(qualifier))
                .in(SINGLETON);

        binder.bind(qualifiedKey(HttpServerInfo.class))
                    .toProvider(new HttpServerInfoProvider(qualifier))
                    .in(SINGLETON);

        if (buildConfigObject(qualifiedKey(HttpServerConfig.class), HttpServerConfig.class, configPrefix).isHttpsEnabled()) {
            configBinder(binder).bindConfig(qualifiedKey(HttpsConfig.class), HttpsConfig.class, configPrefix);
        }

        configBinder(binder).bindConfig(qualifiedKey(HttpServerConfig.class), HttpServerConfig.class, configPrefix);
    }

    private <T> Key<T> qualifiedKey(Class<T> type)
    {
        return qualifier
                .map(annotation -> Key.get(type, annotation))
                .orElseGet(() -> Key.get(type));
    }
}
