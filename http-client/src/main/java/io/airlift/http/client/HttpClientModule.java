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
package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import io.airlift.configuration.ConfigDefaults;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.node.NodeInfo;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class HttpClientModule
        implements Module
{
    protected final String name;
    protected final Class<? extends Annotation> annotation;
    protected Binder binder;

    HttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        this.name = requireNonNull(name, "name is null");
        this.annotation = requireNonNull(annotation, "annotation is null");
    }

    void withConfigDefaults(ConfigDefaults<HttpClientConfig> configDefaults)
    {
        configBinder(binder).bindConfigDefaults(HttpClientConfig.class, annotation, configDefaults);
    }

    @Override
    public void configure(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null");

        // bind the configuration
        configBinder(binder).bindConfig(HttpClientConfig.class, annotation, name);

        // Allow users to bind their own SslContextFactory
        newOptionalBinder(binder, SslContextFactory.Client.class);

        // bind the client
        binder.bind(HttpClient.class).annotatedWith(annotation).toProvider(new HttpClientProvider(name, annotation)).in(Scopes.SINGLETON);

        // kick off the binding for the default filters
        newSetBinder(binder, HttpRequestFilter.class, GlobalFilter.class);

        // kick off the binding for the filter set
        newSetBinder(binder, HttpRequestFilter.class, annotation);

        // kick off the binding for the byte buffer pool
        newOptionalBinder(binder, Key.get(ByteBufferPool.class, annotation));

        // export stats
        newExporter(binder).export(HttpClient.class).annotatedWith(annotation).withGeneratedName();
    }

    public void addAlias(Class<? extends Annotation> alias)
    {
        binder.bind(HttpClient.class).annotatedWith(alias).to(Key.get(HttpClient.class, annotation));
    }

    private static class HttpClientProvider
            implements Provider<HttpClient>
    {
        private final String name;
        private final Class<? extends Annotation> annotation;
        private Injector injector;
        private NodeInfo nodeInfo;
        private OpenTelemetry openTelemetry = OpenTelemetry.noop();
        private Tracer tracer = TracerProvider.noop().get("noop");

        private HttpClientProvider(String name, Class<? extends Annotation> annotation)
        {
            this.name = requireNonNull(name, "name is null");
            this.annotation = requireNonNull(annotation, "annotation is null");
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = injector;
        }

        @Inject(optional = true)
        public void setNodeInfo(NodeInfo nodeInfo)
        {
            this.nodeInfo = nodeInfo;
        }

        @Inject(optional = true)
        public void setOpenTelemetry(OpenTelemetry openTelemetry)
        {
            this.openTelemetry = openTelemetry;
        }

        @Inject(optional = true)
        public void setTracer(Tracer tracer)
        {
            this.tracer = tracer;
        }

        @Override
        public HttpClient get()
        {
            HttpClientConfig config = injector.getInstance(Key.get(HttpClientConfig.class, annotation));
            Optional<String> environment = Optional.ofNullable(nodeInfo).map(NodeInfo::getEnvironment);
            Optional<SslContextFactory.Client> sslContextFactory = injector.getInstance(Key.get(new TypeLiteral<>() {}));
            Optional<ByteBufferPool> byteBufferPool = injector.getInstance(Key.get(new TypeLiteral<>() {}));

            Set<HttpRequestFilter> filters = ImmutableSet.<HttpRequestFilter>builder()
                    .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, GlobalFilter.class)))
                    .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, annotation)))
                    .build();

            Set<HttpStatusListener> httpStatusListeners = ImmutableSet.<HttpStatusListener>builder()
                    .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpStatusListener>>() {}, GlobalFilter.class)))
                    .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpStatusListener>>() {}, annotation)))
                    .build();

            return new JettyHttpClient(name, config, ImmutableList.copyOf(filters), openTelemetry, tracer, environment, sslContextFactory, byteBufferPool, httpStatusListeners);
        }
    }
}
