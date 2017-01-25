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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.google.inject.Injector;
import org.weakref.jmx.Managed;

import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class JmxInspector
        implements Iterable<JmxInspector.InspectorRecord>
{
    private final ImmutableSortedSet<InspectorRecord> inspectorRecords;

    public enum Types
    {
        ATTRIBUTE,
        ACTION
    }

    public static class InspectorRecord implements Comparable<InspectorRecord>
    {
        public final String className;
        public final String objectName;
        public final String description;
        public final Types type;

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            InspectorRecord that = (InspectorRecord) o;

            if (className != null ? !className.equals(that.className) : that.className != null) {
                return false;
            }
            if (objectName != null ? !objectName.equals(that.objectName) : that.objectName != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            return className.hashCode();
        }

        @Override
        public int compareTo(InspectorRecord rhs)
        {
            int diff = objectName.compareTo(rhs.objectName);
            return (diff != 0) ? diff : className.compareTo(rhs.className);
        }

        private InspectorRecord(String className, String objectName, String description, Types type)
        {
            this.className = className;
            this.objectName = objectName;
            this.description = description;
            this.type = type;
        }
    }

    @Inject
    public JmxInspector(Injector injector)
            throws Exception
    {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectInstance> instances = mBeanServer.queryMBeans(null, null);
        Multimap<String, String> nameMap = ArrayListMultimap.create();
        for (ObjectInstance i : instances) {
            nameMap.put(i.getClassName(), i.getObjectName().getCanonicalName());
        }

        ImmutableSortedSet.Builder<InspectorRecord> builder = ImmutableSortedSet.naturalOrder();
        GuiceInjectorIterator injectorIterator = new GuiceInjectorIterator(injector);
        for (Class<?> clazz : injectorIterator) {
            addConfig(nameMap, clazz, builder);
        }

        inspectorRecords = builder.build();
    }

    @Override
    public Iterator<InspectorRecord> iterator()
    {
        return Iterators.unmodifiableIterator(inspectorRecords.iterator());
    }

    private void addConfig(Multimap<String, String> nameMap, Class<?> clazz, ImmutableSortedSet.Builder<InspectorRecord> builder)
            throws InvocationTargetException, IllegalAccessException
    {
        Collection<String> thisNameList = nameMap.get(clazz.getName());
        if (thisNameList != null) {
            for (Method method : clazz.getMethods()) {
                Managed configAnnotation = method.getAnnotation(Managed.class);
                if (configAnnotation != null) {
                    for (String thisName : thisNameList) {
                        builder.add(new InspectorRecord(thisName, method.getName(), configAnnotation.description(), getType(method)));
                    }
                }
            }
        }
    }

    private Types getType(Method method)
    {
        if (method.getReturnType() == Void.TYPE) {
            return Types.ACTION;
        }

        if (method.getParameterTypes().length > 0) {
            return Types.ACTION;
        }
        else {
            return Types.ATTRIBUTE;
        }
    }
}
