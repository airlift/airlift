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
package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Message;
import com.google.inject.spi.PrivateElements;
import com.google.inject.spi.ProviderInstanceBinding;

import java.util.List;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

public class ConfigurationValidator
{
    private final ConfigurationFactory configurationFactory;
    private final WarningsMonitor warningsMonitor;

    public ConfigurationValidator(ConfigurationFactory configurationFactory, WarningsMonitor warningsMonitor)
    {
        this.configurationFactory = configurationFactory;
        this.warningsMonitor = warningsMonitor;
    }

    public List<Message> validate(Module... modules)
    {
        return validate(ImmutableList.copyOf(modules));
    }

    public List<Message> validate(Iterable<? extends Module> modules)
    {
        final List<Message> messages = Lists.newArrayList();


        for (String error : configurationFactory.getInitialErrors()) {
            final Message message = new Message(error);
            messages.add(message);
            configurationFactory.getMonitor().onError(message);
        }

        ElementsIterator elementsIterator = new ElementsIterator(modules);
        for (final Element element : elementsIterator) {
            element.acceptVisitor(new DefaultElementVisitor<Void>()
            {
                @Override
                public <T> Void visit(Binding<T> binding)
                {
                    // look for ConfigurationProviders...
                    if (binding instanceof ProviderInstanceBinding) {
                        ProviderInstanceBinding<?> providerInstanceBinding = (ProviderInstanceBinding<?>) binding;
                        Provider<?> provider = providerInstanceBinding.getProviderInstance();
                        if (provider instanceof ConfigurationProvider) {
                            ConfigurationProvider<?> configurationProvider = (ConfigurationProvider<?>) provider;
                            // give the provider the configuration factory
                            configurationProvider.setConfigurationFactory(configurationFactory);
                            configurationProvider.setWarningsMonitor(warningsMonitor);
                            try {
                                // call the getter which will cause object creation
                                configurationProvider.get();
                            } catch (ConfigurationException e) {
                                // if we got errors, add them to the errors list
                                for (Message message : e.getErrorMessages()) {
                                    messages.add(new Message(singletonList(binding.getSource()), message.getMessage(), message.getCause()));
                                }
                            }
                        }

                    }

                    return null;
                }

                @Override
                public Void visit(PrivateElements privateElements)
                {
                    for (Element element : privateElements.getElements()) {
                        element.acceptVisitor(this);
                    }

                    return null;
                }
            });
        }

        for (String unusedProperty : configurationFactory.getUnusedProperties()) {
            final Message message = new Message(format("Configuration property '%s' was not used", unusedProperty));
            messages.add(message);
            configurationFactory.getMonitor().onError(message);
        }

        return messages;
    }
}
