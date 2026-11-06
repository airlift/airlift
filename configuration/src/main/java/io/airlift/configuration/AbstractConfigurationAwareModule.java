/*
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

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.configuration.ConfigurationFactory.CONFIGURATION_FACTORY_ATTRIBUTE;

public abstract class AbstractConfigurationAwareModule
        extends AbstractModule
        implements ConfigurationAwareModule
{
    @Override
    protected final void configure()
    {
        setup(binder());
    }

    protected void consumeProperty(ConfigPropertyMetadata property)
    {
        configurationFactory().consumeProperty(property);
    }

    protected Map<String, String> getProperties()
    {
        return configurationFactory().getProperties();
    }

    protected Optional<String> getProperty(String name)
    {
        return Optional.ofNullable(getProperties().get(name));
    }

    protected <T> T buildConfigObject(Class<T> configClass)
    {
        return buildConfigObject(configClass, null);
    }

    protected <T> T buildConfigObject(Class<T> configClass, String prefix)
    {
        configBinder(binder()).bindConfig(configClass, prefix);
        return configurationFactory().build(configClass, prefix);
    }

    protected <T> T buildConfigObject(Key<T> key, Class<T> configClass, String prefix)
    {
        configBinder(binder()).bindConfig(key, configClass, prefix);
        return configurationFactory().build(configClass, prefix);
    }

    protected abstract void setup(Binder binder);

    private ConfigurationFactory configurationFactory()
    {
        ConfigurationFactory configurationFactory = (ConfigurationFactory) binder().getAttribute(CONFIGURATION_FACTORY_ATTRIBUTE);
        checkState(configurationFactory != null, "configurationFactory was not set: create the injector with Bootstrap or Guice.withAttribute(CONFIGURATION_FACTORY_ATTRIBUTE, configurationFactory)");
        return configurationFactory;
    }
}
