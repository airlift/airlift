package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.PrivateElements;
import com.proofpoint.guice.ElementsIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigurationModule
        implements Module
{
    private static int index = 0;

    private final List<ConfigBinding> configBindings = new ArrayList<ConfigBinding>();
    private final Map<String, String> properties;

    public ConfigurationModule(Map<String, String> properties, final ElementsIterator elementsIterator)
    {
        this.properties = properties;
        final List<Element> newElements = new ArrayList<Element>();

        for ( final Element element : elementsIterator ) {
            element.acceptVisitor(new DefaultElementVisitor<Void>()
            {
                @Override
                public Void visit(PrivateElements privateElements)
                {
                    for ( Key<?> key : privateElements.getExposedKeys() )
                    {
                        if (ConfigBinding.class.isAssignableFrom(key.getTypeLiteral().getRawType())) {
                            System.out.println("");
                        }
                    }
                    return null;
                }

                public <T> Void visit(Binding<T> binding)
                {
                    Key<?> key = binding.getKey();
                    if (ConfigBinding.class.isAssignableFrom(key.getTypeLiteral().getRawType())) {
                        Binding<ConfigBinding> b = (Binding<ConfigBinding>) binding;

                        b.acceptTargetVisitor(new DefaultBindingTargetVisitor<ConfigBinding, Void>()
                        {
                            public Void visit(InstanceBinding<? extends ConfigBinding> instanceBinding)
                            {
                                configBindings.add(instanceBinding.getInstance());
                                return null;
                            }
                        });
                        elementsIterator.unbindElement(element);
                    }
                    else {
                        visitOther(element);
                    }

                    return null;
                }

                @Override
                protected Void visitOther(Element element)
                {
                    newElements.add(element);
                    return null;
                }
            });
        }
    }

    public void configure(Binder binder)
    {
        ConfigurationFactory factory = new ConfigurationFactory(properties);

        for (ConfigBinding binding : configBindings) {
            Class<Object> configClass = (Class<Object>) binding.getConfigClass();

            Object instance;
            try {
                instance = factory.build(configClass);
            }
            catch (ConfigurationException e) {
                instance = e.getPartial();
                for (String error : e.getErrors()) {
                    binder.addError(error);
                }
            }

            binder.bind(Key.get(configClass)).toInstance(instance);
        }
    }

    public static void bindConfig(Binder binder, Class<?> clazz)
    {
        index++;

        binder.bind(ConfigBinding.class)
                .annotatedWith(Names.named(Integer.toString(index)))
                .toInstance(new ConfigBinding(clazz));
    }

    private static class ConfigBinding
    {
        private final Class<?> clazz;

        public ConfigBinding(Class<?> clazz)
        {
            this.clazz = clazz;
        }

        public Class<?> getConfigClass()
        {
            return clazz;
        }
    }
}
