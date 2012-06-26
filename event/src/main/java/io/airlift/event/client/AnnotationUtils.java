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
package io.airlift.event.client;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;

import static com.google.common.collect.Lists.newArrayList;

class AnnotationUtils
{
    /**
     * Find methods that are tagged with the given annotation somewhere in the hierarchy.
     *
     * @param clazz the class to analyze
     * @param annotation the annotation to find
     * @return the annotated methods
     */
    public static Collection<Method> findAnnotatedMethods(Class<?> clazz, Class<? extends Annotation> annotation)
    {
        Collection<Method> result = newArrayList();

        // gather all publicly available methods, including those declared in a parent
        for (Method method : clazz.getMethods()) {
            // skip methods that are used internally by the vm for implementing covariance, etc.
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            // look for annotations recursively in super-classes or interfaces
            Method managedMethod = findAnnotatedMethod(clazz, annotation, method.getName(), method.getParameterTypes());
            if (managedMethod != null) {
                result.add(managedMethod);
            }
        }

        return result;
    }

    private static Method findAnnotatedMethod(Class<?> clazz, Class<? extends Annotation> annotation, String methodName, Class<?>... paramTypes)
    {
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            if (method.isAnnotationPresent(annotation)) {
                return method;
            }
        }
        catch (NoSuchMethodException e) {
            // ignore
        }

        if (clazz.getSuperclass() != null) {
            Method managedMethod = findAnnotatedMethod(clazz.getSuperclass(), annotation, methodName, paramTypes);
            if (managedMethod != null) {
                return managedMethod;
            }
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            Method managedMethod = findAnnotatedMethod(iface, annotation, methodName, paramTypes);
            if (managedMethod != null) {
                return managedMethod;
            }
        }

        return null;
    }
}
