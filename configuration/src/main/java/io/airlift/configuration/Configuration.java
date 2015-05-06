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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderInstanceBinding;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.singletonList;

public final class Configuration
{
    private Configuration()
    {
    }

    public static List<Message> processConfiguration(ConfigurationFactory configurationFactory, WarningsMonitor warningsMonitor, Module... modules)
    {
        return processConfiguration(configurationFactory, warningsMonitor, ImmutableList.copyOf(modules));
    }

    public static List<Message> processConfiguration(ConfigurationFactory configurationFactory, WarningsMonitor warningsMonitor, Collection<? extends Module> modules)
    {
        // some modules need access to configuration factory so they can lazy register additional config classes
        // initialize configuration factory
        modules.stream()
                .filter(ConfigurationAwareModule.class::isInstance)
                .map(ConfigurationAwareModule.class::cast)
                .forEach(module -> module.setConfigurationFactory(configurationFactory));

        List<Message> messages = Lists.newArrayList();

        ElementsIterator elementsIterator = new ElementsIterator(modules);
        for (Element element : elementsIterator) {
            element.acceptVisitor(new DefaultElementVisitor<Void>()
            {
                @Override
                public <T> Void visit(Binding<T> binding)
                {
                    // look for default configs
                    if (binding instanceof InstanceBinding) {
                        InstanceBinding<T> instanceBinding = (InstanceBinding<T>) binding;
                        if (instanceBinding.getInstance() instanceof ConfigDefaultsHolder) {
                            configurationFactory.registerConfigDefaults((ConfigDefaultsHolder<?>) instanceBinding.getInstance());
                        }
                    }
                    return null;
                }
            });
        }

        for (Element element : elementsIterator) {
            element.acceptVisitor(new DefaultElementVisitor<Void>()
            {
                @Override
                public <T> Void visit(Binding<T> binding)
                {
                    // look for ConfigurationProviders...
                    if (binding instanceof ProviderInstanceBinding) {
                        ProviderInstanceBinding<?> providerInstanceBinding = (ProviderInstanceBinding<?>) binding;
                        Provider<?> provider = providerInstanceBinding.getProviderInstance();
                        if (provider instanceof ConfigurationAwareProvider) {
                            ConfigurationAwareProvider<?> configurationProvider = (ConfigurationAwareProvider<?>) provider;
                            // give the provider the configuration factory
                            configurationProvider.setConfigurationFactory(configurationFactory);
                            configurationProvider.setWarningsMonitor(warningsMonitor);
                            try {
                                // call the getter which will cause object creation
                                configurationProvider.get();
                            }
                            catch (ConfigurationException e) {
                                // if we got errors, add them to the errors list
                                for (Message message : e.getErrorMessages()) {
                                    messages.add(new Message(singletonList(binding.getSource()), message.getMessage(), message.getCause()));
                                }
                            }
                        }

                    }

                    return null;
                }
            });
        }
        return messages;
    }
}
