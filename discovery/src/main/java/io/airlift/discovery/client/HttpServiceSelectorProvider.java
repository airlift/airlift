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
package io.airlift.discovery.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;

import static io.airlift.discovery.client.ServiceTypes.serviceType;

class HttpServiceSelectorProvider
        implements Provider<HttpServiceSelector>
{
    private final String type;
    private Injector injector;

    public HttpServiceSelectorProvider(String type)
    {
        Preconditions.checkNotNull(type);
        this.type = type;
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = injector;
    }

    public HttpServiceSelector get()
    {
        Preconditions.checkNotNull(injector, "injector is null");

        ServiceSelector serviceSelector = injector.getInstance(Key.get(ServiceSelector.class, serviceType(type)));

        HttpServiceSelector httpServiceSelector = new HttpServiceSelectorImpl(serviceSelector);
        return httpServiceSelector;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        HttpServiceSelectorProvider that = (HttpServiceSelectorProvider) o;

        if (!type.equals(that.type)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return type.hashCode();
    }
}
