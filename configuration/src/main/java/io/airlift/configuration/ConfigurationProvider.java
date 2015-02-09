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
package io.airlift.configuration;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Key;

public class ConfigurationProvider<T>
        implements ConfigurationAwareProvider<T>
{
    private final Key<T> key;
    private final Class<T> configClass;
    private final String prefix;
    private ConfigurationFactory configurationFactory;
    private WarningsMonitor warningsMonitor;

    public ConfigurationProvider(Key<T> key, Class<T> configClass, String prefix)
    {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(configClass, "configClass");

        this.key = key;
        this.configClass = configClass;
        this.prefix = prefix;
    }

    @Override
    @Inject
    public void setConfigurationFactory(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = configurationFactory;
    }

    @Override
    @Inject(optional = true)
    public void setWarningsMonitor(WarningsMonitor warningsMonitor)
    {
        this.warningsMonitor = warningsMonitor;
    }

    public Key<T> getKey()
    {
        return key;
    }

    public Class<T> getConfigClass()
    {
        return configClass;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public ConfigurationMetadata<T> getConfigurationMetadata()
    {
        return ConfigurationMetadata.getConfigurationMetadata(configClass);
    }

    public T getDefaultConfig()
    {
        return configurationFactory.getDefaultConfig(key);
    }

    @Override
    public T get()
    {
        Preconditions.checkNotNull(configurationFactory, "configurationFactory");

        return configurationFactory.build(this, warningsMonitor);
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

        ConfigurationProvider<?> that = (ConfigurationProvider<?>) o;

        if (!key.equals(that.key)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return key.hashCode();
    }
}
