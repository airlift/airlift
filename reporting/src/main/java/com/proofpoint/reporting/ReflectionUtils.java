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

import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeOperationsException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

final class ReflectionUtils
{
    private ReflectionUtils()
    {
    }

    private static final Pattern getterOrSetterPattern = Pattern.compile("(get|set|is)(.+)");

    public static Object invoke(Object target, Method method)
            throws MBeanException, ReflectionException
    {
        checkNotNull(target, "target is null");
        checkNotNull(method, "method is null");

        try {
            Object result = method.invoke(target);
            return result;
        }
        catch (InvocationTargetException e) {
            // unwrap exception
            Throwable targetException = e.getTargetException();
            if (targetException instanceof RuntimeException) {
                throw new MBeanException(
                        (RuntimeException) targetException,
                        "RuntimeException occured while invoking " + toSimpleName(method));
            }
            else if (targetException instanceof ReflectionException) {
                // allow ReflectionException to passthrough
                throw (ReflectionException) targetException;
            }
            else if (targetException instanceof MBeanException) {
                // allow MBeanException to passthrough
                throw (MBeanException) targetException;
            }
            else if (targetException instanceof Exception) {
                throw new MBeanException(
                        (Exception) targetException,
                        "Exception occured while invoking " + toSimpleName(method));
            }
            else if (targetException instanceof Error) {
                throw new RuntimeErrorException(
                        (Error) targetException,
                        "Error occured while invoking " + toSimpleName(method));
            }
            else {
                throw new RuntimeErrorException(
                        new AssertionError(targetException),
                        "Unexpected throwable occured while invoking " + toSimpleName(method));
            }
        }
        catch (RuntimeException e) {
            throw new RuntimeOperationsException(e, "RuntimeException occured while invoking " + toSimpleName(method));
        }
        catch (IllegalAccessException e) {
            throw new ReflectionException(e, "IllegalAccessException occured while invoking " + toSimpleName(method));
        }
        catch (Error err) {
            throw new RuntimeErrorException(err, "Error occured while invoking " + toSimpleName(method));
        }
        catch (Exception e) {
            throw new ReflectionException(e, "Exception occured while invoking " + toSimpleName(method));
        }
    }

    private static String toSimpleName(Method method)
    {
        return method.getName() + "()";
    }

    public static boolean isGetter(Method method)
    {
        String methodName = method.getName();
        return (methodName.startsWith("get") || methodName.startsWith("is")) && isValidGetter(method);
    }

    public static String getAttributeName(Method method)
    {
        Matcher matcher = getterOrSetterPattern.matcher(method.getName());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("method does not represent a getter or setter");
        }
        return matcher.group(2);
    }

    public static boolean isValidGetter(Method getter)
    {
        if (getter == null) {
            throw new NullPointerException("getter is null");
        }
        if (getter.getParameterTypes().length != 0) {
            return false;
        }
        if (getter.getReturnType().equals(Void.TYPE)) {
            return false;
        }
        return true;
    }
}
