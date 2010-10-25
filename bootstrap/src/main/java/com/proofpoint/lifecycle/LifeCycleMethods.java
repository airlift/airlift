package com.proofpoint.lifecycle;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class LifeCycleMethods
{
    private final Multimap<Class<? extends Annotation>, Method> methodMap = ArrayListMultimap.create();

    LifeCycleMethods(Class<?> clazz)
    {
        addLifeCycleMethods(clazz, new HashSet<String>(), new HashSet<String>());
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

    private void addLifeCycleMethods(Class<?> clazz, Set<String> usedConstructNames, Set<String> usedDestroyNames)
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
                if ( !usedConstructNames.contains(method.getName()) )
                {
                    usedConstructNames.add(method.getName());
                    methodMap.put(PostConstruct.class, method);
                }
            }
            if ( method.isAnnotationPresent(PreDestroy.class) )
            {
                if ( !usedDestroyNames.contains(method.getName()) )
                {
                    usedDestroyNames.add(method.getName());
                    methodMap.put(PreDestroy.class, method);
                }
            }
        }

        addLifeCycleMethods(clazz.getSuperclass(), usedConstructNames, usedDestroyNames);
        for ( Class<?> face : clazz.getInterfaces() )
        {
            addLifeCycleMethods(face, usedConstructNames, usedDestroyNames);
        }
    }
}
