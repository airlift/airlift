package com.proofpoint.lifecycle;

import org.testng.collections.Maps;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

class LifeCycleMethods
{
    private final Map<Class<? extends Annotation>, Method>      methodMap = Maps.newHashMap();

    LifeCycleMethods(Class<?> clazz)
    {
        addLifeCycleMethods(clazz);
    }

    Method      methodFor(Class<? extends Annotation> annotation)
    {
        return methodMap.get(annotation);
    }

    private void addLifeCycleMethods(Class<?> clazz)
    {
        if ( clazz == null )
        {
            return;
        }

        for ( Method method : clazz.getDeclaredMethods() )
        {
            if ( method.isAnnotationPresent(PostConstruct.class) )
            {
                methodMap.put(PostConstruct.class, method);
            }
            if ( method.isAnnotationPresent(PreDestroy.class) )
            {
                methodMap.put(PreDestroy.class, method);
            }
        }

        addLifeCycleMethods(clazz.getSuperclass());
        for ( Class<?> face : clazz.getInterfaces() )
        {
            addLifeCycleMethods(face);
        }
    }
}
