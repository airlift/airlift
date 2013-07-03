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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.TypeConverterBinding;
import com.proofpoint.reporting.TestExporter.NamedObject;
import org.testng.annotations.BeforeMethod;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.proofpoint.reporting.Util.getUniqueObjectName;

public class TestExporter extends AbstractReportedBeanTest<NamedObject>
{
    private ReportedBeanRegistry registry;

    static class NamedObject
    {
        final ObjectName objectName;
        final Object object;

        NamedObject(ObjectName objectName, Object object)
        {
            this.objectName = objectName;
            this.object = object;
        }

        static NamedObject of(ObjectName objectName, Object object)
        {
            return new NamedObject(objectName, object);
        }
    }

    @Override
    protected Object getObject(NamedObject namedObject)
    {
        return namedObject.object;
    }

    @Override
    protected MBeanInfo getMBeanInfo(NamedObject namedObject)
            throws Exception
    {
        return registry.getReportedBeans().get(namedObject.objectName).getMBeanInfo();
    }

    @Override
    protected Object getAttribute(NamedObject namedObject, String attributeName)
            throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException
    {
        return registry.getReportedBeans().get(namedObject.objectName).getAttribute(attributeName);
    }

    @BeforeMethod
    void setup()
            throws MalformedObjectNameException, InstanceAlreadyExistsException
    {
        registry = new ReportedBeanRegistry();

        objects = new ArrayList<>(2);
        objects.add(NamedObject.of(getUniqueObjectName(), new SimpleObject()));
        objects.add(NamedObject.of(getUniqueObjectName(), new CustomAnnotationObject()));
        objects.add(NamedObject.of(getUniqueObjectName(), new FlattenObject()));
        objects.add(NamedObject.of(getUniqueObjectName(), new CustomFlattenAnnotationObject()));
        objects.add(NamedObject.of(getUniqueObjectName(), new NestedObject()));
        objects.add(NamedObject.of(getUniqueObjectName(), new CustomNestedAnnotationObject()));

        ImmutableSet.Builder<Mapping> mappingBuilder = ImmutableSet.builder();
        ImmutableMap.Builder<Key<?>, Object> instanceBuilder = ImmutableMap.builder();
        for (NamedObject namedObject : objects) {
            Key<?> key = Key.get(namedObject.object.getClass());
            mappingBuilder.add(new Mapping(namedObject.objectName.getCanonicalName(), key));
            instanceBuilder.put(key, namedObject.object);
        }

        new ReportExporter(
                mappingBuilder.build(),
                registry,
                new TestingInjector(instanceBuilder.build()));
    }

    private static class TestingInjector implements Injector
    {
        private final Map<Key<?>,Object> instanceMap;

        public TestingInjector(Map<Key<?>, Object> instanceMap)
        {
            this.instanceMap = instanceMap;
        }

        @Override
        public void injectMembers(Object instance)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> MembersInjector<T> getMembersInjector(Class<T> type)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Key<?>, Binding<?>> getBindings()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Key<?>, Binding<?>> getAllBindings()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Binding<T> getBinding(Key<T> key)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Binding<T> getBinding(Class<T> type)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Binding<T> getExistingBinding(Key<T> key)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Provider<T> getProvider(Key<T> key)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Provider<T> getProvider(Class<T> type)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getInstance(Key<T> key)
        {
            return (T) instanceMap.get(key);
        }

        @Override
        public <T> T getInstance(Class<T> type)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Injector getParent()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Injector createChildInjector(Iterable<? extends Module> modules)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Injector createChildInjector(Module... modules)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Class<? extends Annotation>, Scope> getScopeBindings()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<TypeConverterBinding> getTypeConverterBindings()
        {
            throw new UnsupportedOperationException();
        }
    }
}


