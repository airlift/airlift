/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.discovery.client.balance;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;

import static com.proofpoint.discovery.client.ServiceTypes.serviceType;

public final class HttpServiceBalancerProvider
        implements Provider<HttpServiceBalancer>
{
    private final String type;
    private HttpServiceBalancerFactory serviceBalancerFactory;
    private Injector injector;

    public HttpServiceBalancerProvider(String type)
    {
        Preconditions.checkNotNull(type, "type is null");
        this.type = type;
    }

    @Inject
    public void setInjector(Injector injector)
    {
        Preconditions.checkNotNull(injector, "injector is null");
        this.injector = injector;
    }

    @Inject
    public void setServiceBalancerFactory(HttpServiceBalancerFactory serviceBalancerFactory)
    {
        Preconditions.checkNotNull(serviceBalancerFactory, "serviceBalancerFactory is null");
        this.serviceBalancerFactory = serviceBalancerFactory;
    }

    public HttpServiceBalancer get()
    {
        Preconditions.checkNotNull(serviceBalancerFactory, "serviceBalancerFactory is null");
        Preconditions.checkNotNull(injector, "injector is null");

        ServiceSelectorConfig selectorConfig = injector.getInstance(Key.get(ServiceSelectorConfig.class, serviceType(type)));

        HttpServiceBalancer serviceBalancer = serviceBalancerFactory.createHttpServiceBalancer(type, selectorConfig);
        return serviceBalancer;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(type, serviceBalancerFactory, injector);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final HttpServiceBalancerProvider other = (HttpServiceBalancerProvider) obj;
        return Objects.equal(this.type, other.type) && Objects.equal(this.serviceBalancerFactory, other.serviceBalancerFactory) && Objects.equal(this.injector, other.injector);
    }
}
