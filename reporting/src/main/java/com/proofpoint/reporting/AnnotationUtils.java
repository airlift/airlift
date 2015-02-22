/*
 *  Copyright 2010 Dain Sundstrom
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableSet;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Nested;

import javax.management.Descriptor;
import javax.management.DescriptorKey;
import javax.management.ImmutableDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.lang.String.format;
import static java.util.Arrays.asList;

final class AnnotationUtils
{
    private static final ImmutableSet<Class<? extends Annotation>> REPORTED_ANNOTATIONS = ImmutableSet.of(ReportedAnnotation.class, Nested.class, Flatten.class);
    private static final Set<Class<? extends Annotation>> FLATTEN_ANNOTATION_SET = ImmutableSet.<Class<? extends Annotation>>of(Flatten.class);
    private static final Set<Class<? extends Annotation>> NESTED_ANNOTATION_SET = ImmutableSet.<Class<? extends Annotation>>of(Nested.class);

    private AnnotationUtils()
    {
    }

    public static Descriptor buildDescriptor(Method annotatedMethod)
    {
        return buildDescriptor(annotatedMethod.getAnnotations());
    }

    public static Descriptor buildDescriptor(Annotation... annotations)
    {
        Map<String, Object> fields = new TreeMap<>();

        // process all direct annotations
        for (Annotation annotation : computeWalkSequence(annotations)) {
            processAnnotation(annotation, fields);
        }

        return new ImmutableDescriptor(fields);
    }

    public static List<Annotation> computeWalkSequence(Annotation... annotations)
    {
        Set<Annotation> seen = new HashSet<>();
        List<Annotation> result = new ArrayList<>();

        computeWalkSequence(seen, result, annotations);

        return new ArrayList<>(result);
    }

    private static void computeWalkSequence(Set<Annotation> seen, List<Annotation> result, Annotation... annotations)
    {
        seen.addAll(asList(annotations));

        for (Annotation annotation : annotations) {
            for (Annotation parent : annotation.annotationType().getAnnotations()) {
                if (!seen.contains(parent)) {
                    computeWalkSequence(seen, result, parent);
                }
            }
        }

        Collections.addAll(result, annotations);
    }

    private static void processAnnotation(Annotation annotation, Map<String, Object> fieldsCollector)
    {
        // for each field in the annotation
        for (Method field : annotation.annotationType().getMethods()) {
            // if the field is annotated with the descriptor key
            DescriptorKey descriptorKey = field.getAnnotation(DescriptorKey.class);
            if (descriptorKey == null) {
                continue;
            }

            // name is the name of the method
            String name = descriptorKey.value();

            // invoke method to get the value
            Object value;
            try {
                value = field.invoke(annotation);
            }
            catch (Exception e) {
                Throwable cause = e;
                if (e instanceof InvocationTargetException) {
                    cause = e.getCause();
                }
                throw new RuntimeException(
                        format("Unexpected exception getting value from @DescriptorKey field type: annotationClass=%s, field=%s",
                        annotation.annotationType().getName(), field.getName()), cause);
            }

            // skip null values, since that is the default
            if (value == null) {
                continue;
            }

            // Convert Class and Enum value or array value to String or String array
            // see DescriptorKey javadocs for more info
            if (value instanceof Class) {
                value = ((Class<?>) value).getName();
            }
            else if (value instanceof Enum) {
                value = ((Enum<?>) value).name();
            }
            else if (value.getClass().isArray()) {
                Class<?> componentType = value.getClass().getComponentType();
                if (Class.class.equals(componentType)) {
                    Class<?>[] classArray = (Class<?>[]) value;
                    String[] stringArray = new String[classArray.length];
                    for (int i = 0; i < classArray.length; i++) {
                        if (classArray[i] != null) {
                            stringArray[i] = classArray[i].getName();
                        }
                    }
                    value = stringArray;
                }
                else if (componentType.isEnum()) {
                    Enum<?>[] enumArray = (Enum<?>[]) value;
                    String[] stringArray = new String[enumArray.length];
                    for (int i = 0; i < enumArray.length; i++) {
                        if (enumArray[i] != null) {
                            stringArray[i] = enumArray[i].name();
                        }
                    }
                    value = stringArray;
                }
            }
            else if (value instanceof Annotation) {
                throw new RuntimeException(
                        format("@DescriptorKey can not be applied to an annotation field type: annotationClass=%s, field=%s",
                        annotation.annotationType().getName(),
                        field.getName()));
            }

            fieldsCollector.put(name, value);
        }
    }

    /**
     * Find methods that are tagged as reported somewhere in the hierarchy
     *
     * @param clazz the class to analyze
     * @return a map that associates a concrete method to the actual method tagged as reported
     *         (which may belong to a different class in clazz's hierarchy)
     */
    public static Map<Method, Method> findReportedMethods(Class<?> clazz)
    {
        Map<Method, Method> result = new HashMap<>();
        Set<Signature> foundMethods = new HashSet<>();
        findReportedMethods(clazz, result, foundMethods);

        return result;
    }

    private static void findReportedMethods(Class<?> clazz, Map<Method, Method> result, Set<Signature> foundMethods)
    {
        // gather all available methods
        // this returns everything, even if it's declared in a parent
        for (Method method : clazz.getDeclaredMethods()) {
            // skip methods that are used internally by the vm for implementing covariance, etc
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }

            Signature methodSignature = new Signature(method);
            if (foundMethods.contains(methodSignature)) {
                continue;
            }
            foundMethods.add(methodSignature);

            // look for annotations recursively in superclasses or interfaces
            Method reportedGetter = findReportedMethod(clazz, method.getName(), method.getParameterTypes());
            if (reportedGetter != null) {
                method.setAccessible(true);
                reportedGetter.setAccessible(true);
                result.put(method, reportedGetter);
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            findReportedMethods(superclass, result, foundMethods);
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            findReportedMethods(iface, result, foundMethods);
        }
    }

    public static Method findReportedMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes)
    {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            if (isReportedMethod(method)) {
                return method;
            }
        }
        catch (NoSuchMethodException ignored) {
        }

        if (clazz.getSuperclass() != null) {
            Method reportedGetter = findReportedMethod(clazz.getSuperclass(), methodName, parameterTypes);
            if (reportedGetter != null) {
                return reportedGetter;
            }
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            Method reportedGetter = findReportedMethod(iface, methodName, parameterTypes);
            if (reportedGetter != null) {
                return reportedGetter;
            }
        }

        return null;
    }

    public static boolean isReportedMethod(Method method)
    {
        return isAnnotationPresent(REPORTED_ANNOTATIONS, new HashSet<Class<? extends Annotation>>(), method.getAnnotations());
    }

    public static boolean isFlatten(Method method)
    {
        return method != null && isAnnotationPresent(FLATTEN_ANNOTATION_SET, new HashSet<Class<? extends Annotation>>(), method.getAnnotations());
    }

    public static boolean isNested(Method method)
    {
        return method != null && isAnnotationPresent(NESTED_ANNOTATION_SET, new HashSet<Class<? extends Annotation>>(), method.getAnnotations());
    }

    private static boolean isAnnotationPresent(Set<Class<? extends Annotation>> annotationClasses, Set<Class<? extends Annotation>> processedTypes, Annotation... annotations)
    {
        // are any of the annotations the specified annotation
        for (Annotation annotation : annotations) {
            for (Class<? extends Annotation> annotationClass : annotationClasses) {
                if (annotationClass.isInstance(annotation)) {
                    return true;
                }
            }
        }

        // are any of the annotations annotated with the specified annotation
        for (Annotation annotation : annotations) {
            if (processedTypes.add(annotation.annotationType()) && isAnnotationPresent(annotationClasses, processedTypes, annotation.annotationType().getAnnotations())) {
                return true;
            }
        }

        return false;
    }
}
