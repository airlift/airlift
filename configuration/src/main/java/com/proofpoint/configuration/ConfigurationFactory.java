package com.proofpoint.configuration;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.FixedValue;
import net.sf.cglib.proxy.NoOp;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ConfigurationFactory
{
    private final Map<String, String> properties;

    public ConfigurationFactory(Map<String, String> properties)
    {
        this.properties = properties;
    }

    public <T> T build(Class<T> configClass)
    {
        Errors<T> errors = new Errors<T>();

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
                    errors.add("No value present for '%s' in [%s]", propertyName, method.toGenericString());
                    callbacks.add(NoOp.INSTANCE);
                }
                else {
                    callbacks.add(NoOp.INSTANCE);
                }
            }
            else if (Modifier.isAbstract(method.getModifiers())) {
                errors.add("Method [%s] is abstract but does not have an @Config annotation",
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

        T result = (T) e.create();

        errors.setPartial(result);
        errors.throwIfHasErrors();

        return result;
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

}
