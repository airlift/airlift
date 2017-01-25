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
package io.airlift.bootstrap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static java.lang.String.format;

class LifeCycleMethods
{
    private final Multimap<Class<? extends Annotation>, Method> methodMap = ArrayListMultimap.create();

    LifeCycleMethods(Class<?> clazz)
    {
        addLifeCycleMethods(clazz, new HashSet<>(), new HashSet<>());
    }

    boolean hasFor(Class<? extends Annotation> annotation)
    {
        Collection<Method> methods = methodMap.get(annotation);
        return (methods != null) && (!methods.isEmpty());
    }

    Collection<Method> methodsFor(Class<? extends Annotation> annotation)
    {
        Collection<Method> methods = methodMap.get(annotation);
        return (methods != null) ? methods : new ArrayList<>();
    }

    private void addLifeCycleMethods(Class<?> clazz, Set<String> usedConstructNames, Set<String> usedDestroyNames)
    {
        if (clazz == null) {
            return;
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }

            processMethod(method, PostConstruct.class, usedConstructNames);
            processMethod(method, PreDestroy.class, usedDestroyNames);
        }

        addLifeCycleMethods(clazz.getSuperclass(), usedConstructNames, usedDestroyNames);
        for (Class<?> face : clazz.getInterfaces()) {
            addLifeCycleMethods(face, usedConstructNames, usedDestroyNames);
        }
    }

    private void processMethod(Method method, Class<? extends Annotation> annotationClass, Set<String> usedSet)
    {
        if (method.isAnnotationPresent(annotationClass)) {
            if (!usedSet.contains(method.getName())) {
                if (method.getParameterTypes().length != 0) {
                    throw new UnsupportedOperationException(format("@PostConstruct/@PreDestroy methods cannot have arguments: %s",
                            method.getDeclaringClass().getName() + "." + method.getName() + "(...)"));
                }

                method.setAccessible(true);
                usedSet.add(method.getName());
                methodMap.put(annotationClass, method);
            }
        }
    }
}
