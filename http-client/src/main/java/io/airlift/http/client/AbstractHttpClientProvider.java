/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.airlift.http.client.spnego.KerberosConfig;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public abstract class AbstractHttpClientProvider
        implements Provider<HttpClient>
{
    protected final String name;
    protected final Class<? extends Annotation> annotation;
    protected Injector injector;

    public AbstractHttpClientProvider(String name, Class<? extends Annotation> annotation)
    {
        this.name = requireNonNull(name, "name is null");
        this.annotation = requireNonNull(annotation, "annotation is null");
    }

    public void initialize() {}

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = requireNonNull(injector, "injector is null");
        initialize();
    }

    @PreDestroy
    public void close() {}

    public String getName()
    {
        return name;
    }

    public Class<? extends Annotation> getAnnotation()
    {
        return annotation;
    }

    protected HttpClientConfig getHttpClientConfig()
    {
        return injector.getInstance(Key.get(HttpClientConfig.class, annotation));
    }

    protected KerberosConfig getKerberosConfig()
    {
        return injector.getInstance(KerberosConfig.class);
    }

    protected List<HttpRequestFilter> getHttpRequestFilters()
    {
        Set<HttpRequestFilter> filters = ImmutableSet.<HttpRequestFilter>builder()
                .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, GlobalFilter.class)))
                .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, annotation)))
                .build();
        return ImmutableList.copyOf(filters);
    }
}
