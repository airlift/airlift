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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import io.airlift.configuration.ConfigDefaults;
import io.airlift.http.client.spnego.KerberosConfig;

import java.lang.annotation.Annotation;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class HttpClientModule
        implements Module
{
    protected final String name;
    protected final Class<? extends Annotation> annotation;
    private final Scope scope;
    private final AbstractHttpClientProvider httpClientProvider;
    protected Binder binder;

    HttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        this.name = requireNonNull(name, "name is null");
        this.annotation = requireNonNull(annotation, "annotation is null");
        this.scope = Scopes.SINGLETON;
        this.httpClientProvider = new DefaultHttpClientProvider(name, annotation);
    }

    HttpClientModule(AbstractHttpClientProvider httpClientProvider, Scope scope)
    {
        this.httpClientProvider = requireNonNull(httpClientProvider, "httpClientProvider is null");
        this.name = httpClientProvider.getName();
        this.annotation = httpClientProvider.getAnnotation();
        this.scope = requireNonNull(scope, "scope is null");
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
        configBinder(binder).bindConfig(KerberosConfig.class);
        configBinder(binder).bindConfig(HttpClientConfig.class, annotation, name);

        // bind the client
        binder.bind(HttpClient.class)
                .annotatedWith(annotation)
                .toProvider(httpClientProvider)
                .in(scope);

        // kick off the binding for the default filters
        newSetBinder(binder, HttpRequestFilter.class, GlobalFilter.class);

        // kick off the binding for the filter set
        newSetBinder(binder, HttpRequestFilter.class, annotation);

        // export stats for SINGLETON scoped instances
        if (Scopes.SINGLETON.equals(scope)) {
            newExporter(binder).export(HttpClient.class).annotatedWith(annotation).withGeneratedName();
        }
    }

    public void addAlias(Class<? extends Annotation> alias)
    {
        binder.bind(HttpClient.class).annotatedWith(alias).to(Key.get(HttpClient.class, annotation));
    }
}
