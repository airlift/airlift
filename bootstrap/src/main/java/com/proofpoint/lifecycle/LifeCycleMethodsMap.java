package com.proofpoint.lifecycle;

import com.google.common.collect.Maps;

import java.util.Map;

class LifeCycleMethodsMap
{
    private final Map<Class<?>, LifeCycleMethods>     map = Maps.newHashMap();

    LifeCycleMethods        get(Class<?> clazz)
    {
        LifeCycleMethods methods = map.get(clazz);
        if ( methods == null )
        {
            methods = new LifeCycleMethods(clazz);
            map.put(clazz, methods);
        }
        return methods;
    }
}
