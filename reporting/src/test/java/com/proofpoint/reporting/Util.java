/*
 *  Copyright 2009 Martin Traverso
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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

public class Util
{
    private final static AtomicInteger id = new AtomicInteger(0);
    
    public static ObjectName getUniqueObjectName()
    {
        try {
            return new ObjectName(Util.class.getName() + ":name=instance_" + id.incrementAndGet());
        }
        catch (MalformedObjectNameException e) {
            throw new AssertionError(e);
        }
    }

    public static Method getMethod(Class<?> target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException
    {
        Method method = getMethod0(target, name, parameterTypes);
        if (method != null) {
            return method;
        }

        // Throw a NoSuchMethodException
        return target.getMethod(name, parameterTypes);
    }

    private static Method getMethod0(Class<?> target, String name, Class<?>[] parameterTypes)
    {
        try {
            Method method = target.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }
        catch (NoSuchMethodException ignored) {
        }

        Class<?> superclass = target.getSuperclass();
        if (superclass != null) {
            Method method = getMethod0(superclass, name, parameterTypes);
            if (method != null) {
                return method;
            }
        }

        for (Class<?> iface : target.getInterfaces()) {
            Method method = getMethod0(iface, name, parameterTypes);
            if (method != null) {
                return method;
            }
        }

        return null;
    }
}
