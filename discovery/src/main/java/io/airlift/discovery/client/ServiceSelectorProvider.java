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

import com.google.inject.Injector;
import com.google.inject.Key;

import javax.inject.Inject;
import javax.inject.Provider;

import static io.airlift.discovery.client.ServiceTypes.serviceType;
import static java.util.Objects.requireNonNull;

public class ServiceSelectorProvider
        implements Provider<ServiceSelector>
{
    private final String type;
    private ServiceSelectorFactory serviceSelectorFactory;
    private Injector injector;

    public ServiceSelectorProvider(String type)
    {
        requireNonNull(type, "type is null");
        this.type = type;
    }

    @Inject
    public void setInjector(Injector injector)
    {
        requireNonNull(injector, "injector is null");
        this.injector = injector;
    }

    @Inject
    public void setServiceSelectorFactory(ServiceSelectorFactory serviceSelectorFactory)
    {
        requireNonNull(serviceSelectorFactory, "serviceSelectorFactory is null");
        this.serviceSelectorFactory = serviceSelectorFactory;
    }

    public ServiceSelector get()
    {
        requireNonNull(serviceSelectorFactory, "serviceSelectorFactory is null");
        requireNonNull(injector, "injector is null");

        ServiceSelectorConfig selectorConfig = injector.getInstance(Key.get(ServiceSelectorConfig.class, serviceType(type)));

        ServiceSelector serviceSelector = serviceSelectorFactory.createServiceSelector(type, selectorConfig);
        return serviceSelector;
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

        ServiceSelectorProvider that = (ServiceSelectorProvider) o;

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
