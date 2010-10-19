package com.proofpoint.lifecycle;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;

class LifeCycleMethods
{
    private final Multimap<Class<? extends Annotation>, Method> methodMap = ArrayListMultimap.create();

    LifeCycleMethods(Class<?> clazz)
    {
        addLifeCycleMethods(clazz);
    }

    boolean      hasFor(Class<? extends Annotation> annotation)
    {
        Collection<Method> methods = methodMap.get(annotation);
        return (methods != null) && (methods.size() > 0);
    }

    Collection<Method>      methodsFor(Class<? extends Annotation> annotation)
    {
        Collection<Method> methods = methodMap.get(annotation);
        return (methods != null) ? methods : Lists.<Method>newArrayList();
    }

    private void addLifeCycleMethods(Class<?> clazz)
    {
        if ( clazz == null )
        {
            return;
        }

        for ( Method method : clazz.getDeclaredMethods() )
        {
            if ( method.isSynthetic() || method.isBridge() ) 
            {
                continue;
            }


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
