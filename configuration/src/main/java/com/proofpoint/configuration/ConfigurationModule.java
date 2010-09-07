package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InstanceBinding;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.FixedValue;
import net.sf.cglib.proxy.NoOp;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ConfigurationModule
        implements Module
{
    private static int index = 0;

    private final List<ConfigBinding> configBindings = new ArrayList<ConfigBinding>();
    private final Module baseModule;
    private final Properties properties;

    public ConfigurationModule(Properties properties, Module... modules)
    {
        this.properties = properties;
        final List<Element> newElements = new ArrayList<Element>();

        List<Element> elements = Elements.getElements(modules);

        for (final Element element : elements) {
            element.acceptVisitor(new DefaultElementVisitor<Void>()
            {
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

        baseModule = Elements.getModule(newElements);
    }

    public void configure(Binder binder)
    {
        binder.install(baseModule);

        for (ConfigBinding binding : configBindings) {
            Class<Object> configClass = (Class<Object>) binding.getConfigClass();

            ArrayList<Callback> callbacks = new ArrayList<Callback>();
            final Map<Method, Integer> slots = new HashMap<Method, Integer>();
            callbacks.add(NoOp.INSTANCE);
            int count = 1;
            for (final Method method : configClass.getMethods()) {
                if (method.isAnnotationPresent(Config.class)) {
                    final Config annotation = method.getAnnotation(Config.class);
                    slots.put(method, count++);

                    String propertyName = annotation.value();
                    String value = (String) properties.get(propertyName);

                    if (value == null && method.isAnnotationPresent(Default.class)) {
                        value = method.getAnnotation(Default.class).value();
                    }

                    if (value != null) {
                        final Object finalValue = coerce(method.getReturnType(), value);
                        callbacks.add(new FixedValue()
                        {
                            public Object loadObject()
                                    throws Exception
                            {
                                return finalValue;
                            }
                        });
                    }
                    else if (Modifier.isAbstract(method.getModifiers())) {
                        // no default (via impl or @Default) and no configured value
                        binder.addError("No value present for '%s' in [%s]", propertyName,
                                        method.toGenericString());

                        callbacks.add(NoOp.INSTANCE);
                    }
                    else {
                        callbacks.add(NoOp.INSTANCE);
                    }
                }
                else if (Modifier.isAbstract(method.getModifiers())) {
                    binder.addError("Method [%s] is abstract but does not have an @Config annotation",
                                    method.toGenericString());

                    callbacks.add(NoOp.INSTANCE);
                }
            }

            Enhancer e = new Enhancer();
            e.setSuperclass(configClass);
            e.setCallbackFilter(new CallbackFilter()
            {
                public int accept(Method method)
                {
                    return slots.containsKey(method) ? slots.get(method) : 0;
                }
            });
            e.setCallbacks(callbacks.toArray(new Callback[callbacks.size()]));

            binder.bind(Key.get(configClass)).toInstance(e.create());
        }
    }

    private Object coerce(Class<?> type, String value)
    {
        if (String.class.isAssignableFrom(type)) {
            return value;
        }
        else if (Boolean.class.isAssignableFrom(type) || Boolean.TYPE.isAssignableFrom(type)) {
            return Boolean.valueOf(value);
        }
        else if (Byte.class.isAssignableFrom(type) || Byte.TYPE.isAssignableFrom(type)) {
            return Byte.valueOf(value);
        }
        else if (Short.class.isAssignableFrom(type) || Short.TYPE.isAssignableFrom(type)) {
            return Short.valueOf(value);
        }
        else if (Integer.class.isAssignableFrom(type) || Integer.TYPE.isAssignableFrom(type)) {
            return Integer.valueOf(value);
        }
        else if (Long.class.isAssignableFrom(type) || Long.TYPE.isAssignableFrom(type)) {
            return Long.valueOf(value);
        }
        else if (Float.class.isAssignableFrom(type) || Float.TYPE.isAssignableFrom(type)) {
            return Float.valueOf(value);
        }
        else if (Double.class.isAssignableFrom(type) || Double.TYPE.isAssignableFrom(type)) {
            return Double.valueOf(value);
        }

        return value;
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
