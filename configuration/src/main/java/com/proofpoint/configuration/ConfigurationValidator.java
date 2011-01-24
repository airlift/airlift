package com.proofpoint.configuration;

import com.google.inject.Binding;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ConfigurationException;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.Message;
import com.google.common.collect.Lists;

import java.util.List;
import static java.util.Collections.singletonList;

@SuppressWarnings( { "ThrowableResultOfMethodCallIgnored" })
public class ConfigurationValidator
{
    private final ConfigurationFactory configurationFactory;

    public ConfigurationValidator(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = configurationFactory;
    }

    public List<Message> validate(Module... modules)
    {
        final List<Message> messages = Lists.newArrayList();

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
                            try {
                                // call the getter which will cause object creation
                                configurationProvider.get();
                            }
                            catch ( ConfigurationException e) {
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
