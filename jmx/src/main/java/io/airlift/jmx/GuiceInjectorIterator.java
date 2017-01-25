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
package io.airlift.jmx;

import com.google.inject.Injector;
import com.google.inject.Key;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

class GuiceInjectorIterator implements Iterator<Class<?>>, Iterable<Class<?>>
{
    private final Set<Key<?>> visited = new HashSet<>();
    private final Iterator<Key<?>> keyIterator;
    private final Injector injector;

    private boolean needsReset = true;
    private Class<?> currentClass = null;
    private GuiceDependencyIterator currentDependencyIterator = null;

    /**
     * @param injector the injector to iterate over
     */
    public GuiceInjectorIterator(Injector injector)
    {
        this.injector = injector;
        keyIterator = injector.getBindings().keySet().iterator();
    }

    @Override
    public boolean hasNext()
    {
        checkReset();
        return (currentClass != null);
    }

    @Override
    public Class<?> next()
    {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        needsReset = true;
        return currentClass;
    }

    @Override
    public Iterator<Class<?>> iterator()
    {
        return new GuiceInjectorIterator(injector);
    }

    @Override
    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    private void checkReset()
    {
        if (!needsReset) {
            return;
        }
        needsReset = false;

        currentClass = null;
        if (currentDependencyIterator != null) {
            if (currentDependencyIterator.hasNext()) {
                currentClass = currentDependencyIterator.next();
            }
            else {
                currentDependencyIterator = null;
            }
        }

        while ((currentClass == null) && keyIterator.hasNext()) {
            Key<?> key = keyIterator.next();
            currentClass = parseKey(visited, key);
            if (currentClass == null) {
                continue;
            }

            currentDependencyIterator = new GuiceDependencyIterator(key.getTypeLiteral());
            currentDependencyIterator = currentDependencyIterator.substituteVisitedSet(visited);
        }
    }

    static Class<?> parseKey(Set<Key<?>> visited, Key<?> key)
    {
        if (visited.contains(key)) {
            return null;
        }
        visited.add(key);

        Class<?> clazz;
        Type type = key.getTypeLiteral().getType();
        if (type instanceof GenericArrayType) {
            type = ((GenericArrayType) type).getGenericComponentType();
        }
        if (type instanceof Class) {
            clazz = (Class<?>) type;
        }
        else {
            clazz = key.getTypeLiteral().getRawType();
        }

        return clazz;
    }
}
